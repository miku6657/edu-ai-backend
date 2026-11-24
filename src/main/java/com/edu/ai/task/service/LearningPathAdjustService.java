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
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

@Service
public class LearningPathAdjustService {

    private static final ZoneId ZONE =
            ZoneId.of("Asia/Shanghai");

    private final JdbcTemplate jdbc;
    private final CoursePermissionService permissions;

    public LearningPathAdjustService(
            JdbcTemplate jdbc,
            CoursePermissionService permissions) {
        this.jdbc = jdbc;
        this.permissions = permissions;
    }

    private record PathInfo(
            long courseId,
            int weeklyHours,
            LocalDate targetDate,
            LocalDate startDate
    ) {}

    private record Stage(
            long id,
            int sequenceNo,
            int weekNo,
            int minutes,
            LocalDate dueDate,
            String status,
            LocalDateTime completedAt
    ) {}

    public record AdjustmentItem(
            String itemId,
            int oldWeek,
            int newWeek,
            LocalDate oldDueDate,
            LocalDate newDueDate
    ) {}

    public record AdjustmentPreview(
            boolean feasible,
            String fingerprint,
            String message,
            int changedCount,
            List<AdjustmentItem> items
    ) {}

    private PathInfo loadPath(
            long pathId, long studentId) {

        List<PathInfo> rows = jdbc.query(
                """
                SELECT p.course_id,
                       g.weekly_hours,
                       g.target_date,
                       DATE(p.create_time) AS start_date
                FROM student_learning_path p
                JOIN student_learning_goal g
                  ON g.id = p.goal_id
                WHERE p.id = ?
                  AND p.student_id = ?
                  AND p.status = 'ACTIVE'
                """,
                (rs, n) -> new PathInfo(
                        rs.getLong("course_id"),
                        rs.getInt("weekly_hours"),
                        rs.getDate("target_date").toLocalDate(),
                        rs.getDate("start_date").toLocalDate()
                ),
                pathId, studentId
        );

        if (rows.isEmpty()) {
            throw new BusinessException(
                    404, "学习路径不存在或不可调整"
            );
        }

        PathInfo path = rows.get(0);
        permissions.requireActiveRead(
                path.courseId(), studentId
        );

        return path;
    }

    private List<Stage> loadStages(long pathId) {

        return jdbc.query(
                """
                SELECT id, sequence_no, week_no,
                       planned_minutes, planned_due_date,
                       status, completed_at
                FROM student_learning_path_item
                WHERE path_id = ?
                ORDER BY sequence_no, id
                """,
                (rs, n) -> {
                    var completed =
                            rs.getTimestamp("completed_at");

                    return new Stage(
                            rs.getLong("id"),
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
            PathInfo path,
            List<Stage> stages,
            LocalDate today) {

        StringBuilder value = new StringBuilder()
                .append(pathId).append('|')
                .append(path.weeklyHours()).append('|')
                .append(path.targetDate()).append('|')
                .append(path.startDate()).append('|')
                .append(today);

        for (Stage stage : stages) {
            value.append('|')
                    .append(stage.id()).append(':')
                    .append(stage.weekNo()).append(':')
                    .append(stage.minutes()).append(':')
                    .append(stage.dueDate()).append(':')
                    .append(stage.status()).append(':')
                    .append(stage.completedAt());
        }

        try {
            byte[] digest = MessageDigest
                    .getInstance("SHA-256")
                    .digest(value.toString()
                            .getBytes(StandardCharsets.UTF_8));

            return HexFormat.of().formatHex(digest);

        } catch (Exception e) {
            throw new IllegalStateException(
                    "无法生成学习计划校验码", e);
        }
    }

    private AdjustmentPreview calculate(
            long pathId,
            PathInfo path,
            List<Stage> stages) {

        LocalDate today = LocalDate.now(ZONE);
        String token = fingerprint(
                pathId, path, stages, today
        );

        int weeklyCapacity = path.weeklyHours() * 60;

        if (weeklyCapacity <= 0) {
            throw new BusinessException(
                    409, "每周学习时间无效"
            );
        }

        if (today.isAfter(path.targetDate())) {
            return new AdjustmentPreview(
                    false, token,
                    "目标日期已过期，需要重新设定目标",
                    0, List.of()
            );
        }

        // 当前处于学习路径的第几周。
        int currentWeek = (int) Math.max(
                1,
                ChronoUnit.DAYS.between(
                        path.startDate(), today
                ) / 7 + 1
        );

        LocalDate currentWeekStart =
                path.startDate().plusWeeks(currentWeek - 1L);

        // 当前周已经实际完成的学习时长。
        int completedThisWeek = stages.stream()
                .filter(s -> "COMPLETED".equals(s.status()))
                .filter(s -> s.completedAt() != null)
                .filter(s -> {
                    LocalDate day =
                            s.completedAt().toLocalDate();
                    return !day.isBefore(currentWeekStart)
                            && day.isBefore(
                                currentWeekStart.plusWeeks(1)
                            );
                })
                .mapToInt(Stage::minutes)
                .sum();

        int week = currentWeek;
        int used = Math.min(
                weeklyCapacity, completedThisWeek
        );

        List<AdjustmentItem> changes =
                new ArrayList<>();

        for (Stage stage : stages) {

            if ("COMPLETED".equals(stage.status())) {
                continue;
            }

            if (!"PENDING".equals(stage.status())) {
                throw new BusinessException(
                        409,
                        "存在不支持调整的任务状态"
                );
            }

            if (stage.minutes() <= 0
                    || stage.minutes() > weeklyCapacity) {
                return new AdjustmentPreview(
                        false, token,
                        "某阶段任务超过每周可用时间，"
                                + "需要重新拆分阶段任务",
                        0, List.of()
                );
            }

            // 每条阶段任务保持完整，不拆成多条。
            if (used + stage.minutes() > weeklyCapacity) {
                week++;
                used = 0;
            }

            LocalDate due = path.startDate()
                    .plusWeeks(week - 1L)
                    .plusDays(6);

            if (due.isAfter(path.targetDate())) {
                return new AdjustmentPreview(
                        false, token,
                        "剩余任务无法在目标日期前完成，"
                                + "建议延长目标日期或增加每周学习时间",
                        0, List.of()
                );
            }

            used += stage.minutes();

            if (stage.weekNo() != week
                    || !stage.dueDate().equals(due)) {

                changes.add(new AdjustmentItem(
                        String.valueOf(stage.id()),
                        stage.weekNo(),
                        week,
                        stage.dueDate(),
                        due
                ));
            }
        }

        return new AdjustmentPreview(
                true,
                token,
                changes.isEmpty()
                        ? "当前学习路径无需调整"
                        : "建议重新安排剩余学习任务",
                changes.size(),
                changes
        );
    }

    public AdjustmentPreview preview(
            long pathId, long studentId) {

        PathInfo path = loadPath(pathId, studentId);

        return calculate(
                pathId, path, loadStages(pathId)
        );
    }

    /**
     * 学生确认建议后才写入数据库。
     * 全部变更与关联 DDL 在同一事务中完成。
     */
    @Transactional(rollbackFor = Exception.class)
    public AdjustmentPreview apply(
            long pathId,
            long studentId,
            String expectedFingerprint) {

        // 锁定整条路径，串行化同一路径的调整。
        List<Long> owners = jdbc.queryForList(
                """
                SELECT student_id
                FROM student_learning_path
                WHERE id = ? AND student_id = ?
                FOR UPDATE
                """,
                Long.class, pathId, studentId
        );

        if (owners.isEmpty()) {
            throw new BusinessException(
                    404, "学习路径不存在"
            );
        }

        PathInfo path = loadPath(pathId, studentId);
        List<Stage> stages = loadStages(pathId);

        AdjustmentPreview preview =
                calculate(pathId, path, stages);

        if (expectedFingerprint == null
                || !expectedFingerprint.equals(
                    preview.fingerprint())) {
            throw new BusinessException(
                    409,
                    "学习计划已发生变化，请重新预览"
            );
        }

        if (!preview.feasible()) {
            throw new BusinessException(
                    409, preview.message()
            );
        }

        for (AdjustmentItem item : preview.items()) {

            long itemId = Long.parseLong(item.itemId());

            int updated = jdbc.update(
                    """
                    UPDATE student_learning_path_item
                    SET week_no = ?,
                        planned_due_date = ?
                    WHERE id = ?
                      AND path_id = ?
                      AND status = 'PENDING'
                    """,
                    item.newWeek(),
                    item.newDueDate(),
                    itemId,
                    pathId
            );

            if (updated != 1) {
                throw new BusinessException(
                        409, "阶段任务已变化，请重新预览"
                );
            }

            // 与个人 DDL 保持同一个截止日期。
            // 已完成的 DDL 不允许被修改。
            int ddlUpdated = jdbc.update(
                    """
                    UPDATE ddl_task
                    SET due_date = ?,
                        due_at = ?,
                        status = CASE
                            WHEN ? < NOW() THEN 'OVERDUE'
                            ELSE 'ACTIVE'
                        END,
                        reminded_t24h = 0,
                        reminded_t6h = 0,
                        reminded_overdue = 0,
                        updated_at = NOW()
                    WHERE learning_path_item_id = ?
                      AND user_id = ?
                      AND status NOT IN ('COMPLETED', 'CANCELED')
                    """,
                    item.newDueDate(),
                    item.newDueDate().atTime(23, 59, 59),
                    item.newDueDate().atTime(23, 59, 59),
                    itemId,
                    studentId
            );

            if (ddlUpdated != 1) {
                throw new BusinessException(
                        409,
                        "个人DDL任务不存在或状态已变化"
                );
            }
        }

        if (!preview.items().isEmpty()) {
            jdbc.update(
                    """
                    INSERT INTO student_learning_path_adjustment
                        (path_id, student_id,
                         reason, changed_items)
                    VALUES (?, ?, ?, ?)
                    """,
                    pathId,
                    studentId,
                    "根据当前学习进度重新安排剩余任务",
                    preview.changedCount()
            );
        }

        return preview;
    }
}
