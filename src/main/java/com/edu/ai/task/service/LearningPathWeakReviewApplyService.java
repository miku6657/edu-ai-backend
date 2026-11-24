package com.edu.ai.task.service;

import com.edu.ai.common.BusinessException;
import com.edu.ai.course.service.CoursePermissionService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

@Service
public class LearningPathWeakReviewApplyService {

    private final JdbcTemplate jdbc;
    private final CoursePermissionService permissions;
    private final LearningPathWeakReviewService reviews;
    private final LearningPathTaskSyncService ddlSync;

    public LearningPathWeakReviewApplyService(
            JdbcTemplate jdbc,
            CoursePermissionService permissions,
            LearningPathWeakReviewService reviews,
            LearningPathTaskSyncService ddlSync) {

        this.jdbc = jdbc;
        this.permissions = permissions;
        this.reviews = reviews;
        this.ddlSync = ddlSync;
    }

    private record Path(
            long courseId,
            int weeklyMinutes,
            LocalDate start,
            LocalDate target,
            int totalMinutes,
            int totalWeeks
    ) {}

    private record Stage(
            long id,
            long pointId,
            int segmentNo,
            int sequenceNo,
            int weekNo,
            int minutes,
            LocalDate due,
            String status,
            LocalDateTime completedAt
    ) {}

    public record Move(
            String itemId,
            int oldWeek,
            int newWeek,
            LocalDate oldDueDate,
            LocalDate newDueDate
    ) {}

    public record Addition(
            String pointId,
            String title,
            int segmentNo,
            int weekNo,
            int plannedMinutes,
            LocalDate dueDate
    ) {}

    public record Plan(
            boolean feasible,
            String fingerprint,
            String message,
            int additionalMinutes,
            List<Move> moves,
            List<Addition> additions
    ) {}

    private Path path(long pathId, long studentId) {

        List<Path> rows = jdbc.query(
                """
                SELECT
                    p.course_id,
                    g.weekly_hours,
                    g.target_date,
                    DATE(p.create_time) AS start_date,
                    p.total_minutes,
                    p.total_weeks
                FROM student_learning_path p
                JOIN student_learning_goal g
                  ON g.id = p.goal_id
                WHERE p.id = ?
                  AND p.student_id = ?
                  AND p.status = 'ACTIVE'
                """,
                (rs, n) -> new Path(
                        rs.getLong("course_id"),
                        rs.getInt("weekly_hours") * 60,
                        rs.getDate("start_date").toLocalDate(),
                        rs.getDate("target_date").toLocalDate(),
                        rs.getInt("total_minutes"),
                        rs.getInt("total_weeks")
                ),
                pathId,
                studentId
        );

        if (rows.isEmpty()) {
            throw new BusinessException(
                    404,
                    "学习路径不存在或不可调整"
            );
        }

        Path result = rows.get(0);

        permissions.requireActiveRead(
                result.courseId(),
                studentId
        );

        return result;
    }

    private List<Stage> stages(
            long pathId,
            boolean lock) {

        String sql =
                """
                SELECT
                    id,
                    point_id,
                    segment_no,
                    sequence_no,
                    week_no,
                    planned_minutes,
                    planned_due_date,
                    status,
                    completed_at
                FROM student_learning_path_item
                WHERE path_id = ?
                ORDER BY sequence_no, id
                """
                + (lock ? " FOR UPDATE" : "");

        return jdbc.query(
                sql,
                (rs, n) -> {
                    var completed =
                            rs.getTimestamp("completed_at");

                    return new Stage(
                            rs.getLong("id"),
                            rs.getLong("point_id"),
                            rs.getInt("segment_no"),
                            rs.getInt("sequence_no"),
                            rs.getInt("week_no"),
                            rs.getInt("planned_minutes"),
                            rs.getDate("planned_due_date")
                                    .toLocalDate(),
                            rs.getString("status"),
                            completed == null
                                    ? null
                                    : completed.toLocalDateTime()
                    );
                },
                pathId
        );
    }

    private String fingerprint(
            long pathId,
            Path path,
            List<Stage> stages,
            LearningPathWeakReviewService.WeakReviewPreview review,
            LocalDate today) {

        StringBuilder value = new StringBuilder();

        value.append(pathId)
                .append('|')
                .append(path)
                .append('|')
                .append(today);

        for (Stage stage : stages) {
            value.append('|').append(stage);
        }

        for (var item : review.items()) {
            value.append('|').append(item);
        }

        try {
            byte[] bytes =
                    MessageDigest.getInstance("SHA-256")
                            .digest(
                                    value.toString().getBytes(
                                            StandardCharsets.UTF_8
                                    )
                            );

            return HexFormat.of().formatHex(bytes);

        } catch (Exception e) {
            throw new IllegalStateException(
                    "无法计算学习计划校验码",
                    e
            );
        }
    }

    /**
     * 计算新增复习任务及现有任务的排期建议。
     * 此方法只计算，不写入数据库。
     */
    private Plan calculate(
            long pathId,
            long studentId,
            Path path,
            List<Stage> stages) {

        LocalDate today =
                StudyWeekCapacity.today();

        var review =
                reviews.preview(pathId, studentId);

        String token =
                fingerprint(
                        pathId,
                        path,
                        stages,
                        review,
                        today
                );

        if (today.isAfter(path.target())
                || path.weeklyMinutes() <= 0) {

            return new Plan(
                    false,
                    token,
                    "学习目标日期已过或每周学时无效",
                    0,
                    List.of(),
                    List.of()
            );
        }

        if (review.additionalTotalMinutes() == 0) {
            return new Plan(
                    true,
                    token,
                    "当前没有需要新增的复习任务",
                    0,
                    List.of(),
                    List.of()
            );
        }

        int week = (int) Math.max(
                1,
                ChronoUnit.DAYS.between(
                        path.start(),
                        today
                ) / 7 + 1
        );

        LocalDate weekStart =
                path.start().plusWeeks(week - 1L);

        int completedThisWeek = stages.stream()
                .filter(s ->
                        "COMPLETED".equals(s.status())
                                && s.completedAt() != null
                )
                .filter(s -> {
                    LocalDate date =
                            s.completedAt().toLocalDate();

                    return !date.isBefore(weekStart)
                            && date.isBefore(
                                    weekStart.plusDays(7)
                            );
                })
                .mapToInt(Stage::minutes)
                .sum();

        int capacity =
                StudyWeekCapacity.capacity(
                        path.start(),
                        path.target(),
                        week,
                        path.weeklyMinutes()
                );

        int used =
                Math.min(completedThisWeek, capacity);

        List<Move> moves = new ArrayList<>();
        List<Addition> additions = new ArrayList<>();

        Map<Long, Integer> maxSegment =
                new HashMap<>();

        // 先安排原有未完成阶段任务
        for (Stage stage : stages) {

            maxSegment.merge(
                    stage.pointId(),
                    stage.segmentNo(),
                    Math::max
            );

            if ("COMPLETED".equals(stage.status())) {
                continue;
            }

            if (!"PENDING".equals(stage.status())
                    || stage.minutes() <= 0) {

                return new Plan(
                        false,
                        token,
                        "存在状态异常的阶段任务",
                        0,
                        List.of(),
                        List.of()
                );
            }

            while (used + stage.minutes() > capacity) {

                week++;

                capacity =
                        StudyWeekCapacity.capacity(
                                path.start(),
                                path.target(),
                                week,
                                path.weeklyMinutes()
                        );

                used = 0;

                if (capacity <= 0) {
                    return new Plan(
                            false,
                            token,
                            "目标日期前无法容纳现有阶段任务",
                            0,
                            List.of(),
                            List.of()
                    );
                }
            }

            LocalDate dueDate =
                    StudyWeekCapacity.dueDate(
                            path.start(),
                            path.target(),
                            week
                    );

            if (stage.weekNo() != week
                    || !stage.due().equals(dueDate)) {

                moves.add(
                        new Move(
                                Long.toString(stage.id()),
                                stage.weekNo(),
                                week,
                                stage.due(),
                                dueDate
                        )
                );
            }

            used += stage.minutes();
        }

        // 再追加薄弱知识点复习阶段
        int addedMinutes = 0;

        for (var reviewItem : review.items()) {

            long pointId =
                    Long.parseLong(reviewItem.pointId());

            int remaining =
                    reviewItem.additionalMinutes();

            if (remaining <= 0) {
                continue;
            }

            addedMinutes += remaining;

            int segment =
                    maxSegment.getOrDefault(pointId, 0);

            while (remaining > 0) {

                if (used >= capacity) {

                    week++;

                    capacity =
                            StudyWeekCapacity.capacity(
                                    path.start(),
                                    path.target(),
                                    week,
                                    path.weeklyMinutes()
                            );

                    used = 0;
                }

                if (capacity <= 0) {
                    return new Plan(
                            false,
                            token,
                            "目标日期前学习容量不足，请延后目标或增加每周学时",
                            0,
                            List.of(),
                            List.of()
                    );
                }

                int minutes =
                        Math.min(
                                remaining,
                                capacity - used
                        );

                segment++;

                additions.add(
                        new Addition(
                                reviewItem.pointId(),
                                reviewItem.title(),
                                segment,
                                week,
                                minutes,
                                StudyWeekCapacity.dueDate(
                                        path.start(),
                                        path.target(),
                                        week
                                )
                        )
                );

                remaining -= minutes;
                used += minutes;
            }

            maxSegment.put(pointId, segment);
        }

        return new Plan(
                true,
                token,
                "计划新增" + additions.size()
                        + "个复习阶段，追加"
                        + addedMinutes + "分钟",
                addedMinutes,
                List.copyOf(moves),
                List.copyOf(additions)
        );
    }

    /**
     * 学生预览新增复习任务。
     * 不修改数据库。
     */
    public Plan preview(
            long pathId,
            long studentId) {

        Path path = path(pathId, studentId);

        return calculate(
                pathId,
                studentId,
                path,
                stages(pathId, false)
        );
    }

    /**
     * 学生确认后，将复习任务写入学习路径，
     * 并同步个人DDL。
     */
    @Transactional(rollbackFor = Exception.class)
    public Plan apply(
            long pathId,
            long studentId,
            String expectedFingerprint) {

        List<Long> owned = jdbc.queryForList(
                """
                SELECT id
                FROM student_learning_path
                WHERE id = ?
                  AND student_id = ?
                  AND status = 'ACTIVE'
                FOR UPDATE
                """,
                Long.class,
                pathId,
                studentId
        );

        if (owned.isEmpty()) {
            throw new BusinessException(
                    404,
                    "学习路径不存在或不可调整"
            );
        }

        Path path =
                path(pathId, studentId);

        List<Stage> stages =
                stages(pathId, true);

        Plan plan =
                calculate(
                        pathId,
                        studentId,
                        path,
                        stages
                );

        if (expectedFingerprint == null
                || !expectedFingerprint.equals(
                        plan.fingerprint())) {

            throw new BusinessException(
                    409,
                    "复习建议已变化，请重新预览"
            );
        }

        if (!plan.feasible()) {
            throw new BusinessException(
                    409,
                    plan.message()
            );
        }

        if (plan.additionalMinutes() == 0
                || plan.additions().isEmpty()) {

            throw new BusinessException(
                    409,
                    "没有可新增的复习任务"
            );
        }

        // 1. 修改原有未完成阶段的截止时间
        for (Move move : plan.moves()) {

            long itemId =
                    Long.parseLong(move.itemId());

            int changed = jdbc.update(
                    """
                    UPDATE student_learning_path_item
                    SET week_no = ?,
                        planned_due_date = ?
                    WHERE id = ?
                      AND path_id = ?
                      AND status = 'PENDING'
                    """,
                    move.newWeek(),
                    move.newDueDate(),
                    itemId,
                    pathId
            );

            if (changed != 1) {
                throw new BusinessException(
                        409,
                        "阶段任务状态发生变化"
                );
            }

            // 同步关联的个人DDL截止日期
            int synced = jdbc.update(
                    """
                    UPDATE ddl_task
                    SET due_date = ?,
                        due_at = ?,
                        status = CASE
                            WHEN ? < NOW()
                            THEN 'OVERDUE'
                            ELSE 'ACTIVE'
                        END,
                        reminded_t24h = 0,
                        reminded_t6h = 0,
                        reminded_overdue = 0,
                        updated_at = NOW()
                    WHERE learning_path_item_id = ?
                      AND user_id = ?
                      AND status IN ('ACTIVE', 'OVERDUE')
                    """,
                    move.newDueDate(),
                    move.newDueDate().atTime(23, 59, 59),
                    move.newDueDate().atTime(23, 59, 59),
                    itemId,
                    studentId
            );

            if (synced != 1) {
                throw new BusinessException(
                        409,
                        "个人DDL状态已变化，请重试"
                );
            }
        }

        // 2. 为薄弱知识点新增复习阶段
        int sequence = stages.stream()
                .mapToInt(Stage::sequenceNo)
                .max()
                .orElse(0);

        int lastWeek =
                path.totalWeeks();

        for (Addition addition : plan.additions()) {

            sequence++;

            int inserted = jdbc.update(
                    """
                    INSERT INTO student_learning_path_item
                        (
                            path_id,
                            point_id,
                            segment_no,
                            sequence_no,
                            week_no,
                            planned_minutes,
                            planned_due_date,
                            status
                        )
                    VALUES (?, ?, ?, ?, ?, ?, ?, 'PENDING')
                    """,
                    pathId,
                    Long.parseLong(addition.pointId()),
                    addition.segmentNo(),
                    sequence,
                    addition.weekNo(),
                    addition.plannedMinutes(),
                    addition.dueDate()
            );

            if (inserted != 1) {
                throw new BusinessException(
                        409,
                        "新增复习阶段失败"
                );
            }

            lastWeek = Math.max(
                    lastWeek,
                    addition.weekNo()
            );
        }

        // 3. 更新路径总学习时长
        int pathUpdated = jdbc.update(
                """
                UPDATE student_learning_path
                SET total_minutes = total_minutes + ?,
                    total_weeks = ?
                WHERE id = ?
                  AND student_id = ?
                  AND status = 'ACTIVE'
                """,
                plan.additionalMinutes(),
                lastWeek,
                pathId,
                studentId
        );

        if (pathUpdated != 1) {
            throw new BusinessException(
                    409,
                    "学习路径更新失败"
            );
        }

        // 4. 为新增阶段生成个人DDL
        // 复用第24版已有的幂等同步服务
        ddlSync.syncPath(pathId, studentId);

        // 5. 写入调整日志
        jdbc.update(
                """
                INSERT INTO student_learning_path_adjustment
                    (
                        path_id,
                        student_id,
                        reason,
                        changed_items
                    )
                VALUES (?, ?, ?, ?)
                """,
                pathId,
                studentId,
                "依据高频提问追加复习阶段",
                plan.moves().size()
                        + plan.additions().size()
        );

        return plan;
    }
}
