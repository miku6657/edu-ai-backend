package com.edu.ai.task.service;

import com.edu.ai.common.BusinessException;
import com.edu.ai.course.service.CoursePermissionService;
import com.edu.ai.system.entity.User;
import com.edu.ai.system.enums.UserRole;
import com.edu.ai.system.mapper.UserMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Statement;
import java.time.LocalDate;
import java.util.*;

@Service
public class LearningPathService {

    private final JdbcTemplate jdbc;
    private final CoursePermissionService permissions;
    private final UserMapper userMapper;
    private final LearningPathTaskSyncService taskSyncService;
    private final QuestionKnowledgeService questionKnowledgeService;

    public LearningPathService(
            JdbcTemplate jdbc,
            CoursePermissionService permissions,
            UserMapper userMapper,
            LearningPathTaskSyncService taskSyncService,
            QuestionKnowledgeService questionKnowledgeService) {
        this.jdbc = jdbc;
        this.permissions = permissions;
        this.userMapper = userMapper;
        this.taskSyncService = taskSyncService;
        this.questionKnowledgeService = questionKnowledgeService;
    }

    public record Point(
            Long id,
            Long courseId,
            String title,
            int estimatedMinutes,
            int sortOrder,
            String matchKeywords
    ) {}

    public record PathItem(
            String id,
            String pointId,
            String title,
            int segmentNo,
            int sequenceNo,
            int weekNo,
            int plannedMinutes,
            LocalDate plannedDueDate,
            String status
    ) {}

    public record PathResult(
            String pathId,
            String goalId,
            String courseId,
            int totalMinutes,
            int totalWeeks,
            String status,
            List<PathItem> items
    ) {}

    private void requireStudent(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null
                || !Integer.valueOf(1).equals(user.getStatus())
                || !UserRole.STUDENT.name().equals(user.getRole())) {
            throw new BusinessException(
                    403, "只有学生可以生成个人学习路径");
        }
    }

    private List<Point> points(Long courseId) {
        return jdbc.query(
                """
                SELECT id, course_id, title,
                       estimated_minutes, sort_order,
                       match_keywords
                FROM course_knowledge_point
                WHERE course_id = ?
                ORDER BY sort_order, id
                """,
                (rs, rowNum) -> new Point(
                        rs.getLong("id"),
                        rs.getLong("course_id"),
                        rs.getString("title"),
                        rs.getInt("estimated_minutes"),
                        rs.getInt("sort_order"),
                        rs.getString("match_keywords")
                ),
                courseId
        );
    }

    public List<Point> listPoints(
            Long courseId, Long userId) {
        permissions.requireActiveRead(courseId, userId);
        return points(courseId);
    }

    private Long insertWithId(
            String sql, Object... args) {

        GeneratedKeyHolder holder = new GeneratedKeyHolder();

        jdbc.update(connection -> {
            var ps = connection.prepareStatement(
                    sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            return ps;
        }, holder);

        Number key = holder.getKey();

        if (key == null) {
            throw new BusinessException(
                    500, "数据库未返回新增记录ID");
        }

        return key.longValue();
    }

    @Transactional(rollbackFor = Exception.class)
    public Long addPoint(
            Long courseId,
            Long userId,
            String title,
            Integer estimatedMinutes,
            Integer sortOrder,
            String matchKeywords) {

        permissions.requireManage(courseId, userId);

        if (title == null || title.isBlank()
                || title.length() > 200
                || estimatedMinutes == null
                || estimatedMinutes < 1
                || estimatedMinutes > 10080) {
            throw new BusinessException(
                    400, "知识点名称或预计学时不合法");
        }

        String keywords = matchKeywords == null
                ? ""
                : matchKeywords.trim();

        if (keywords.length() > 500) {
            throw new BusinessException(
                    400, "知识点匹配关键词不能超过500字");
        }

        return insertWithId(
                """
                INSERT INTO course_knowledge_point
                    (course_id, title, estimated_minutes,
                     sort_order, match_keywords)
                VALUES (?, ?, ?, ?, ?)
                """,
                courseId,
                title.trim(),
                estimatedMinutes,
                sortOrder == null ? 0 : sortOrder,
                keywords
        );
    }

    @Transactional(rollbackFor = Exception.class)
    public void updatePointKeywords(
            Long courseId,
            Long pointId,
            Long userId,
            String matchKeywords) {

        permissions.requireManage(courseId, userId);

        if (matchKeywords == null
                || matchKeywords.length() > 500) {
            throw new BusinessException(
                    400, "关键词内容不合法");
        }

        int updated = jdbc.update(
                """
                UPDATE course_knowledge_point
                SET match_keywords = ?
                WHERE id = ?
                  AND course_id = ?
                """,
                matchKeywords.trim(),
                pointId,
                courseId
        );

        if (updated != 1) {
            throw new BusinessException(
                    404, "课程知识点不存在");
        }
    }

    /**
     * 教师设置一个先修关系。
     * 点和前置点必须同属一门课程。
     * 若形成循环，事务会回滚。
     */
    @Transactional(rollbackFor = Exception.class)
    public void addDependency(
            Long courseId,
            Long userId,
            Long pointId,
            Long prerequisiteId) {

        permissions.requireManage(courseId, userId);

        if (pointId.equals(prerequisiteId)) {
            throw new BusinessException(
                    400, "知识点不能依赖自身");
        }

        Map<Long, Point> map = new HashMap<>();
        for (Point p : points(courseId)) {
            map.put(p.id(), p);
        }

        if (!map.containsKey(pointId)
                || !map.containsKey(prerequisiteId)) {
            throw new BusinessException(
                    400, "先修关系必须属于同一课程");
        }

        jdbc.update(
                """
                INSERT IGNORE INTO course_knowledge_dependency
                    (point_id, prerequisite_id)
                VALUES (?, ?)
                """,
                pointId, prerequisiteId
        );

        // 对整门课程执行一次拓扑排序校验。
        // 若检测到环，抛异常并回滚本次插入。
        orderPoints(
                courseId,
                points(courseId),
                Map.of()
        );
    }

    @Transactional(rollbackFor = Exception.class)
    public void setMastery(
            Long courseId,
            Long pointId,
            Long studentId,
            int level) {

        requireStudent(studentId);
        permissions.requireActiveRead(courseId, studentId);

        if (level < 0 || level > 3) {
            throw new BusinessException(
                    400, "掌握程度必须在0到3之间");
        }

        boolean exists = points(courseId).stream()
                .anyMatch(p -> p.id().equals(pointId));

        if (!exists) {
            throw new BusinessException(
                    404, "课程知识点不存在");
        }

        jdbc.update(
                """
                INSERT INTO student_knowledge_mastery
                    (student_id, point_id, mastery_level)
                VALUES (?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    mastery_level = VALUES(mastery_level),
                    update_time = NOW()
                """,
                studentId, pointId, level
        );
    }

    private Map<Long, Integer> mastery(
            Long courseId, Long studentId) {

        Map<Long, Integer> result = new HashMap<>();

        jdbc.query(
                """
                SELECT m.point_id, m.mastery_level
                FROM student_knowledge_mastery m
                JOIN course_knowledge_point p
                  ON p.id = m.point_id
                WHERE p.course_id = ?
                  AND m.student_id = ?
                """,
                rs -> {
                    result.put(
                            rs.getLong("point_id"),
                            rs.getInt("mastery_level")
                    );
                },
                courseId, studentId
        );

        return result;
    }

    /**
     * 学习路径生成使用的有效掌握程度。
     *
     * 自评与系统推断分开保存，仅在生成时合并。
     * 等级越小，需要的复习时间越多。
     */
    private Map<Long, Integer> effectiveMastery(
            Long courseId,
            Long studentId) {

        Map<Long, Integer> result =
                new HashMap<>(mastery(courseId, studentId));

        Map<Long, Integer> inferred =
                questionKnowledgeService.inferredMastery(
                        courseId,
                        studentId
                );

        for (var entry : inferred.entrySet()) {
            Long pointId = entry.getKey();

            int manualLevel =
                    result.getOrDefault(pointId, 2);

            int inferredLevel = entry.getValue();

            result.put(
                    pointId,
                    Math.min(manualLevel, inferredLevel)
            );
        }

        return result;
    }

    public Map<String, Integer> listMyMastery(
            Long courseId,
            Long studentId) {

        requireStudent(studentId);
        permissions.requireActiveRead(courseId, studentId);

        Map<Long, Integer> stored =
                mastery(courseId, studentId);

        Map<String, Integer> result = new LinkedHashMap<>();

        for (Point point : points(courseId)) {
            result.put(
                    point.id().toString(),
                    stored.getOrDefault(point.id(), 2)
            );
        }

        return result;
    }

    /**
     * 拓扑排序：
     * 先确保前置知识点排在后置知识点之前。
     * 同时可学习的知识点，薄弱程度优先。
     */
    private List<Point> orderPoints(
            Long courseId,
            List<Point> points,
            Map<Long, Integer> mastery) {

        Map<Long, Point> pointMap = new HashMap<>();
        Map<Long, Integer> indegree = new HashMap<>();
        Map<Long, List<Long>> next = new HashMap<>();

        for (Point p : points) {
            pointMap.put(p.id(), p);
            indegree.put(p.id(), 0);
            next.put(p.id(), new ArrayList<>());
        }

        jdbc.query(
                """
                SELECT d.point_id, d.prerequisite_id
                FROM course_knowledge_dependency d
                JOIN course_knowledge_point p
                  ON p.id = d.point_id
                WHERE p.course_id = ?
                """,
                rs -> {
                    long id = rs.getLong("point_id");
                    long prerequisite =
                            rs.getLong("prerequisite_id");

                    if (!pointMap.containsKey(prerequisite)) {
                        throw new BusinessException(
                                409, "存在跨课程先修关系");
                    }

                    indegree.merge(id, 1, Integer::sum);
                    next.get(prerequisite).add(id);
                },
                courseId
        );

        Comparator<Point> comparator =
                Comparator.comparingInt(
                        (Point p) ->
                                mastery.getOrDefault(p.id(), 2))
                        .thenComparingInt(Point::sortOrder)
                        .thenComparing(Point::id);

        PriorityQueue<Point> ready =
                new PriorityQueue<>(comparator);

        for (Point p : points) {
            if (indegree.get(p.id()) == 0) {
                ready.add(p);
            }
        }

        List<Point> ordered = new ArrayList<>();

        while (!ready.isEmpty()) {
            Point current = ready.poll();
            ordered.add(current);

            for (Long dependent : next.get(current.id())) {
                int count = indegree.merge(
                        dependent, -1, Integer::sum);

                if (count == 0) {
                    ready.add(pointMap.get(dependent));
                }
            }
        }

        if (ordered.size() != points.size()) {
            throw new BusinessException(
                    409, "知识点先修关系存在循环");
        }

        return ordered;
    }

    private int adjustedMinutes(
            Point point,
            Map<Long, Integer> mastery) {

        int level = mastery.getOrDefault(point.id(), 2);

        double factor = switch (level) {
            case 0 -> 1.5;
            case 1 -> 1.25;
            default -> 1.0;
        };

        return (int) Math.ceil(
                point.estimatedMinutes() * factor);
    }

    /**
     * 生成学习路径。
     * 相同目标单重复请求直接返回原路径。
     */
    @Transactional(rollbackFor = Exception.class)
    public PathResult generate(
            Long goalId, Long studentId) {

        requireStudent(studentId);

        var goals = jdbc.query(
                """
                SELECT id, course_id, weekly_hours,
                       target_date, status
                FROM student_learning_goal
                WHERE id = ? AND student_id = ?
                FOR UPDATE
                """,
                (rs, rowNum) -> Map.of(
                        "id", rs.getLong("id"),
                        "courseId", rs.getLong("course_id"),
                        "weeklyHours", rs.getInt("weekly_hours"),
                        "targetDate",
                            rs.getDate("target_date").toLocalDate(),
                        "status", rs.getString("status")
                ),
                goalId, studentId
        );

        if (goals.isEmpty()) {
            throw new BusinessException(
                    404, "学习目标不存在");
        }

        var goal = goals.get(0);

        Long courseId = (Long) goal.get("courseId");
        permissions.requireActiveRead(courseId, studentId);

        if (!"CONFIRMED".equals(goal.get("status"))) {
            throw new BusinessException(
                    409, "请先确认学习目标");
        }

        // 同一目标只生成一次，避免重复阶段任务。
        List<Long> existing = jdbc.queryForList(
                """
                SELECT id FROM student_learning_path
                WHERE goal_id = ?
                """,
                Long.class, goalId
        );

        if (!existing.isEmpty()) {

            Long existingPathId = existing.get(0);

            taskSyncService.syncPath(
                    existingPathId,
                    studentId
            );

            return getPath(existingPathId, studentId);
        }

        List<Point> coursePoints = points(courseId);

        if (coursePoints.isEmpty()) {
            throw new BusinessException(
                    409, "教师尚未配置课程知识点");
        }

        Map<Long, Integer> mastery =
                effectiveMastery(courseId, studentId);

        List<Point> ordered =
                orderPoints(courseId, coursePoints, mastery);

        int weeklyMinutes =
                (Integer) goal.get("weeklyHours") * 60;

        LocalDate start = StudyWeekCapacity.today();
        LocalDate target =
                (LocalDate) goal.get("targetDate");

        if (weeklyMinutes <= 0 || target.isBefore(start)) {
            throw new BusinessException(
                    409, "每周学习时间或目标日期无效"
            );
        }

        int availableWeeks =
                StudyWeekCapacity.availableWeeks(start, target);

        int totalMinutes = ordered.stream()
                .mapToInt(p -> adjustedMinutes(p, mastery))
                .sum();

        int availableMinutes = 0;
        int requiredWeeks = 0;

        for (int w = 1; w <= availableWeeks; w++) {
            availableMinutes += StudyWeekCapacity.capacity(
                    start, target, w, weeklyMinutes
            );

            requiredWeeks = w;

            if (availableMinutes >= totalMinutes) {
                break;
            }
        }

        if (availableMinutes < totalMinutes) {
            throw new BusinessException(
                    409,
                    "目标日期前可用学习时间不足，" +
                    "预计需要" + totalMinutes +
                    "分钟，当前仅有" +
                    availableMinutes + "分钟"
            );
        }

        Long pathId = insertWithId(
                """
                INSERT INTO student_learning_path
                    (goal_id, student_id, course_id,
                     total_minutes, total_weeks,
                     status, create_time)
                VALUES (?, ?, ?, ?, ?, 'ACTIVE', NOW())
                """,
                goalId,
                studentId,
                courseId,
                totalMinutes,
                requiredWeeks
        );

        int week = 1;
        int usedMinutes = 0;
        int sequence = 0;

        for (Point point : ordered) {

            int remaining =
                    adjustedMinutes(point, mastery);
            int segment = 0;

            while (remaining > 0) {

                int capacity = StudyWeekCapacity.capacity(
                        start, target, week, weeklyMinutes
                );

                if (capacity <= 0) {
                    throw new BusinessException(
                            409, "学习任务已超过目标完成日期"
                    );
                }

                if (usedMinutes >= capacity) {
                    week++;
                    usedMinutes = 0;
                    continue;
                }

                int assigned = Math.min(
                        remaining, capacity - usedMinutes
                );

                segment++;
                sequence++;

                LocalDate due =
                        StudyWeekCapacity.dueDate(
                                start, target, week
                        );

                jdbc.update(
                        """
                        INSERT INTO student_learning_path_item
                            (path_id, point_id, segment_no,
                             sequence_no, week_no,
                             planned_minutes,
                             planned_due_date, status)
                        VALUES (?, ?, ?, ?, ?, ?, ?, 'PENDING')
                        """,
                        pathId,
                        point.id(),
                        segment,
                        sequence,
                        week,
                        assigned,
                        due
                );

                remaining -= assigned;
                usedMinutes += assigned;
            }
        }

        taskSyncService.syncPath(pathId, studentId);

        return getPath(pathId, studentId);
    }

    public PathResult getPath(
            Long pathId, Long studentId) {

        requireStudent(studentId);

        var paths = jdbc.query(
                """
                SELECT goal_id, course_id,
                       total_minutes, total_weeks, status
                FROM student_learning_path
                WHERE id = ? AND student_id = ?
                """,
                (rs, rowNum) -> Map.of(
                        "goalId", rs.getLong("goal_id"),
                        "courseId", rs.getLong("course_id"),
                        "minutes", rs.getInt("total_minutes"),
                        "weeks", rs.getInt("total_weeks"),
                        "status", rs.getString("status")
                ),
                pathId, studentId
        );

        if (paths.isEmpty()) {
            throw new BusinessException(
                    404, "学习路径不存在");
        }

        var path = paths.get(0);

        Long courseId = (Long) path.get("courseId");
        permissions.requireActiveRead(courseId, studentId);

        List<PathItem> items = jdbc.query(
                """
                SELECT i.*, p.title
                FROM student_learning_path_item i
                JOIN course_knowledge_point p
                  ON p.id = i.point_id
                WHERE i.path_id = ?
                ORDER BY i.sequence_no
                """,
                (rs, rowNum) -> new PathItem(
                        String.valueOf(rs.getLong("id")),
                        String.valueOf(rs.getLong("point_id")),
                        rs.getString("title"),
                        rs.getInt("segment_no"),
                        rs.getInt("sequence_no"),
                        rs.getInt("week_no"),
                        rs.getInt("planned_minutes"),
                        rs.getDate("planned_due_date")
                                .toLocalDate(),
                        rs.getString("status")
                ),
                pathId
        );

        return new PathResult(
                pathId.toString(),
                String.valueOf(path.get("goalId")),
                courseId.toString(),
                (Integer) path.get("minutes"),
                (Integer) path.get("weeks"),
                (String) path.get("status"),
                items
        );
    }

    /**
     * 根据已确认的学习目标查询已有学习路径。
     * 不存在时返回 null，不自动生成新路径。
     */
    public PathResult getByGoal(
            Long goalId,
            Long studentId) {

        requireStudent(studentId);

        List<Long> pathIds = jdbc.queryForList(
                """
                SELECT id
                FROM student_learning_path
                WHERE goal_id = ?
                  AND student_id = ?
                LIMIT 1
                """,
                Long.class,
                goalId,
                studentId
        );

        if (pathIds.isEmpty()) {
            // 避免把他人的目标与不存在的目标都误当作
            // 可以任意查询的记录。
            Long count = jdbc.queryForObject(
                    """
                    SELECT COUNT(*)
                    FROM student_learning_goal
                    WHERE id = ?
                      AND student_id = ?
                    """,
                    Long.class,
                    goalId,
                    studentId
            );

            if (count == null || count == 0) {
                throw new BusinessException(
                        404, "学习目标不存在"
                );
            }

            return null;
        }

        return getPath(pathIds.get(0), studentId);
    }
}
