package com.edu.ai.task.service;

import com.edu.ai.common.BusinessException;
import com.edu.ai.course.service.CoursePermissionService;
import com.edu.ai.system.entity.User;
import com.edu.ai.system.enums.UserRole;
import com.edu.ai.system.mapper.UserMapper;
import com.edu.ai.task.dto.KnowledgeMapVO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
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
            String matchKeywords,

            // F2知识点体系新增字段
            Long chapterId,
            Long parentId,
            int difficulty
    ) {}

    private record PointInput(
            String title,
            int estimatedMinutes,
            int sortOrder,
            String matchKeywords,
            Long chapterId,
            Long parentId,
            int difficulty
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

    /**
     * 学习路径阶段完成进度。
     * 与前端 LearningPathProgress 类型对应。
     */
    public record PathProgress(
            int total,
            int completed,
            int percentage
    ) {}

    /**
     * 查询当前学生自己的学习路径进度。
     */
    public PathProgress getProgress(
            Long pathId,
            Long studentId) {

        // 复用已有的学生身份、路径归属和课程权限检查
        PathResult path = getPath(pathId, studentId);

        int total = path.items().size();

        int completed = (int) path.items()
                .stream()
                .filter(item ->
                        "COMPLETED".equals(item.status()))
                .count();

        int percentage = total == 0
                ? 0
                : (int) Math.round(
                        completed * 100.0 / total
                );

        return new PathProgress(
                total,
                completed,
                percentage
        );
    }

    private void requireStudent(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null
                || !Integer.valueOf(1).equals(user.getStatus())
                || !UserRole.STUDENT.name().equals(user.getRole())) {
            throw new BusinessException(
                    403, "只有学生可以生成个人学习路径");
        }
    }

    /**
     * 查询指定课程的全部知识点。
     *
     * 同时供知识点体系维护、学习路径生成、
     * 先修依赖校验等功能使用。
     */
    private List<Point> points(Long courseId) {

        return jdbc.query(
                """
                SELECT
                    id,
                    course_id,
                    title,
                    estimated_minutes,
                    sort_order,
                    match_keywords,
                    chapter_id,
                    parent_id,
                    difficulty
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

                        rs.getString("match_keywords"),

                        rs.getObject(
                                "chapter_id",
                                Long.class
                        ),

                        rs.getObject(
                                "parent_id",
                                Long.class
                        ),

                        rs.getInt("difficulty")
                ),
                courseId
        );
    }

    /**
     * 统一校验知识点新增及修改参数。
     */
    private PointInput validatePointInput(
            String title,
            Integer estimatedMinutes,
            Integer sortOrder,
            String matchKeywords,
            Long chapterId,
            Long parentId,
            Integer difficulty) {

        if (title == null
                || title.isBlank()
                || title.trim().length() > 200) {

            throw new BusinessException(
                    400,
                    "知识点名称不能为空且不能超过200个字符"
            );
        }

        if (estimatedMinutes == null
                || estimatedMinutes < 1
                || estimatedMinutes > 10080) {

            throw new BusinessException(
                    400,
                    "预计学习时间必须在1至10080分钟之间"
            );
        }

        int order = sortOrder == null
                ? 0
                : sortOrder;

        if (order < 0) {

            throw new BusinessException(
                    400, "知识点排序不能小于0"
            );
        }

        String keywords = matchKeywords == null
                ? ""
                : matchKeywords.trim();

        if (keywords.length() > 500) {

            throw new BusinessException(
                    400,
                    "知识点匹配关键词不能超过500字"
            );
        }

        int level = difficulty == null
                ? 2
                : difficulty;

        if (level < 1 || level > 3) {

            throw new BusinessException(
                    400,
                    "知识点难度必须在1至3之间"
            );
        }

        if (chapterId != null && chapterId <= 0) {

            throw new BusinessException(
                    400, "章节ID不合法"
            );
        }

        if (parentId != null && parentId <= 0) {

            throw new BusinessException(
                    400, "父级知识点ID不合法"
            );
        }

        return new PointInput(
                title.trim(),
                estimatedMinutes,
                order,
                keywords,
                chapterId,
                parentId,
                level
        );
    }

    /**
     * 校验课程管理权限，并锁定课程记录。
     *
     * 所有修改知识点层级的业务必须在事务内调用。
     */
    private void lockPointCourse(
            Long courseId,
            Long userId) {

        permissions.requireManage(
                courseId,
                userId
        );

        List<Integer> statuses = jdbc.queryForList(
                """
                SELECT status
                FROM course
                WHERE id = ?
                FOR UPDATE
                """,
                Integer.class,
                courseId
        );

        if (statuses.isEmpty()) {

            throw new BusinessException(
                    404, "课程不存在"
            );
        }

        if (!Integer.valueOf(1).equals(
                statuses.get(0))) {

            throw new BusinessException(
                    403, "课程已停用"
            );
        }
    }

    /**
     * 校验知识点所属章节和父子层级。
     */
    private void validatePointHierarchy(
            Long courseId,
            Long pointId,
            PointInput input) {

        Long chapterId = input.chapterId();
        Long parentId = input.parentId();

        // 1. 校验章节归属
        if (chapterId != null) {

            List<Long> chapters = jdbc.queryForList(
                    """
                    SELECT id
                    FROM course_chapter
                    WHERE id = ?
                      AND course_id = ?
                    FOR UPDATE
                    """,
                    Long.class,
                    chapterId,
                    courseId
            );

            if (chapters.isEmpty()) {

                throw new BusinessException(
                        400,
                        "所选章节不存在或不属于当前课程"
                );
            }
        }

        // 2. 获取当前课程知识点结构
        // 使用锁定读，避免读取旧的事务快照。
        List<Point> allPoints = jdbc.query(
                """
                SELECT
                    id,
                    course_id,
                    title,
                    estimated_minutes,
                    sort_order,
                    match_keywords,
                    chapter_id,
                    parent_id,
                    difficulty
                FROM course_knowledge_point
                WHERE course_id = ?
                ORDER BY id
                FOR UPDATE
                """,
                (rs, rowNum) -> new Point(
                        rs.getLong("id"),
                        rs.getLong("course_id"),
                        rs.getString("title"),
                        rs.getInt("estimated_minutes"),
                        rs.getInt("sort_order"),
                        rs.getString("match_keywords"),
                        rs.getObject("chapter_id", Long.class),
                        rs.getObject("parent_id", Long.class),
                        rs.getInt("difficulty")
                ),
                courseId
        );

        Map<Long, Point> pointMap = new HashMap<>();

        for (Point point : allPoints) {

            pointMap.put(point.id(), point);
        }

        // 3. 修改时校验目标知识点归属
        Point existing = null;

        if (pointId != null) {

            existing = pointMap.get(pointId);

            if (existing == null) {

                throw new BusinessException(
                        404,
                        "当前课程中不存在该知识点"
                );
            }
        }

        // 4. 校验父级知识点
        if (parentId != null) {

            if (Objects.equals(
                    pointId,
                    parentId)) {

                throw new BusinessException(
                        400,
                        "知识点不能将自身设置为父级"
                );
            }

            Point parent = pointMap.get(parentId);

            if (parent == null) {

                throw new BusinessException(
                        400,
                        "父级知识点不存在或不属于当前课程"
                );
            }

            // 父子知识点必须位于同一个章节
            if (!Objects.equals(
                    parent.chapterId(),
                    chapterId)) {

                throw new BusinessException(
                        400,
                        "父子知识点必须属于同一章节"
                );
            }

            // 5. 沿父级链向上检查循环关系
            Set<Long> visited = new HashSet<>();

            Long currentId = parentId;

            while (currentId != null) {

                if (Objects.equals(
                        pointId,
                        currentId)) {

                    throw new BusinessException(
                            400,
                            "不能将子孙知识点设置为父级"
                    );
                }

                if (!visited.add(currentId)) {

                    throw new BusinessException(
                            409,
                            "现有知识点层级存在循环关系"
                    );
                }

                Point current = pointMap.get(currentId);

                if (current == null) {

                    throw new BusinessException(
                            409,
                            "知识点层级关系不完整"
                    );
                }

                currentId = current.parentId();
            }
        }

        // 6. 如果当前知识点包含子节点，
        // 暂时禁止直接跨章节移动。
        if (existing != null
                && !Objects.equals(
                        existing.chapterId(),
                        chapterId)) {

            boolean hasChildren =
                    allPoints.stream().anyMatch(
                            point -> Objects.equals(
                                    point.parentId(),
                                    pointId
                            )
                    );

            if (hasChildren) {

                throw new BusinessException(
                        409,
                        "该知识点包含子知识点，请先调整子知识点所属关系"
                );
            }
        }
    }

    public List<Point> listPoints(
            Long courseId, Long userId) {
        permissions.requireActiveRead(courseId, userId);
        return points(courseId);
    }

    /**
     * 查询指定课程的完整知识地图。
     *
     * 教师、管理员和已加入课程的学生均可访问。
     */
    @Transactional(
            readOnly = true,
            isolation = Isolation.REPEATABLE_READ
    )
    public KnowledgeMapVO getKnowledgeMap(
            Long courseId,
            Long userId) {

        // 1. 校验课程访问权限。
        permissions.requireActiveRead(
                courseId,
                userId
        );

        // 2. 查询课程章节。
        List<KnowledgeMapVO.ChapterNode> chapters =
                jdbc.query(
                        """
                        SELECT
                            id,
                            chapter_name,
                            sort_order
                        FROM course_chapter
                        WHERE course_id = ?
                        ORDER BY sort_order, id
                        """,
                        (rs, rowNum) ->
                                new KnowledgeMapVO.ChapterNode(

                                        String.valueOf(
                                                rs.getLong("id")
                                        ),

                                        rs.getString(
                                                "chapter_name"
                                        ),

                                        rs.getInt(
                                                "sort_order"
                                        )
                                ),
                        courseId
                );

        // 3. 复用已有知识点查询方法。
        List<Point> coursePoints =
                points(courseId);

        List<KnowledgeMapVO.PointNode> nodes =
                coursePoints.stream()
                        .map(point ->
                                new KnowledgeMapVO.PointNode(

                                        point.id().toString(),

                                        point.title(),

                                        point.chapterId() == null
                                                ? null
                                                : point.chapterId()
                                                        .toString(),

                                        point.parentId() == null
                                                ? null
                                                : point.parentId()
                                                        .toString(),

                                        point.difficulty(),

                                        point.estimatedMinutes(),

                                        point.sortOrder(),

                                        point.matchKeywords()
                                )
                        )
                        .toList();

        // 4. 查询当前课程的先修关系。
        // 两端知识点都必须属于当前课程。
        List<KnowledgeMapVO.DependencyEdge> dependencies =
                jdbc.query(
                        """
                        SELECT
                            d.point_id,
                            d.prerequisite_id
                        FROM course_knowledge_dependency d
                        JOIN course_knowledge_point p
                          ON p.id = d.point_id
                        JOIN course_knowledge_point pre
                          ON pre.id = d.prerequisite_id
                        WHERE p.course_id = ?
                          AND pre.course_id = ?
                        ORDER BY
                            d.point_id,
                            d.prerequisite_id
                        """,
                        (rs, rowNum) ->
                                new KnowledgeMapVO.DependencyEdge(

                                        String.valueOf(
                                                rs.getLong("point_id")
                                        ),

                                        String.valueOf(
                                                rs.getLong(
                                                    "prerequisite_id"
                                                )
                                        )
                                ),
                        courseId,
                        courseId
                );

        // 5. 统计知识地图信息。
        int unclassifiedCount = 0;

        int basicCount = 0;
        int intermediateCount = 0;
        int advancedCount = 0;

        long totalEstimatedMinutes = 0;

        for (Point point : coursePoints) {

            if (point.chapterId() == null) {

                unclassifiedCount++;
            }

            switch (point.difficulty()) {

                case 1 -> basicCount++;

                case 2 -> intermediateCount++;

                case 3 -> advancedCount++;

                default -> {
                    // 异常难度值不计入三级统计。
                }
            }

            totalEstimatedMinutes +=
                    point.estimatedMinutes();
        }

        KnowledgeMapVO.Statistics statistics =
                new KnowledgeMapVO.Statistics(

                        chapters.size(),

                        coursePoints.size(),

                        dependencies.size(),

                        unclassifiedCount,

                        basicCount,

                        intermediateCount,

                        advancedCount,

                        totalEstimatedMinutes
                );

        // 6. 返回完整知识地图。
        return new KnowledgeMapVO(
                courseId.toString(),
                chapters,
                nodes,
                dependencies,
                statistics
        );
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

    /**
     * 新增课程知识点。
     */
    @Transactional(rollbackFor = Exception.class)
    public Long addPoint(
            Long courseId,
            Long userId,
            String title,
            Integer estimatedMinutes,
            Integer sortOrder,
            String matchKeywords,
            Long chapterId,
            Long parentId,
            Integer difficulty) {

        lockPointCourse(
                courseId,
                userId
        );

        PointInput input = validatePointInput(
                title,
                estimatedMinutes,
                sortOrder,
                matchKeywords,
                chapterId,
                parentId,
                difficulty
        );

        validatePointHierarchy(
                courseId,
                null,
                input
        );

        try {

            return insertWithId(
                    """
                    INSERT INTO course_knowledge_point (
                        course_id,
                        title,
                        estimated_minutes,
                        sort_order,
                        match_keywords,
                        chapter_id,
                        parent_id,
                        difficulty
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    courseId,
                    input.title(),
                    input.estimatedMinutes(),
                    input.sortOrder(),
                    input.matchKeywords(),
                    input.chapterId(),
                    input.parentId(),
                    input.difficulty()
            );

        } catch (DuplicateKeyException e) {

            throw new BusinessException(
                    409,
                    "当前章节的同级知识点已存在相同名称"
            );
        }
    }

    /**
     * 修改课程知识点完整信息。
     */
    @Transactional(rollbackFor = Exception.class)
    public void updatePoint(
            Long courseId,
            Long pointId,
            Long userId,
            String title,
            Integer estimatedMinutes,
            Integer sortOrder,
            String matchKeywords,
            Long chapterId,
            Long parentId,
            Integer difficulty) {

        lockPointCourse(
                courseId,
                userId
        );

        PointInput input = validatePointInput(
                title,
                estimatedMinutes,
                sortOrder,
                matchKeywords,
                chapterId,
                parentId,
                difficulty
        );

        validatePointHierarchy(
                courseId,
                pointId,
                input
        );

        try {

            jdbc.update(
                    """
                    UPDATE course_knowledge_point
                    SET
                        title = ?,
                        estimated_minutes = ?,
                        sort_order = ?,
                        match_keywords = ?,
                        chapter_id = ?,
                        parent_id = ?,
                        difficulty = ?,
                        update_time = CURRENT_TIMESTAMP
                    WHERE id = ?
                      AND course_id = ?
                    """,
                    input.title(),
                    input.estimatedMinutes(),
                    input.sortOrder(),
                    input.matchKeywords(),
                    input.chapterId(),
                    input.parentId(),
                    input.difficulty(),
                    pointId,
                    courseId
            );

        } catch (DuplicateKeyException e) {

            throw new BusinessException(
                    409,
                    "当前章节的同级知识点已存在相同名称"
            );
        }
    }

    /**
     * 检查知识点是否存在业务关联。
     */
    private boolean hasPointReference(
            String sql,
            Object... args) {

        Long count = jdbc.queryForObject(
                sql,
                Long.class,
                args
        );

        return count != null && count > 0;
    }

    /**
     * 安全删除课程知识点。
     *
     * 仅允许删除没有子节点、先修关系、
     * 文档关联和学生学习记录的知识点。
     */
    @Transactional(rollbackFor = Exception.class)
    public void deletePoint(
            Long courseId,
            Long pointId,
            Long userId) {

        // 1. 校验管理权限并获取课程级事务锁。
        lockPointCourse(
                courseId,
                userId
        );

        // 2. 校验知识点存在且属于当前课程。
        List<Long> rows = jdbc.queryForList(
                """
                SELECT id
                FROM course_knowledge_point
                WHERE id = ?
                  AND course_id = ?
                FOR UPDATE
                """,
                Long.class,
                pointId,
                courseId
        );

        if (rows.isEmpty()) {

            throw new BusinessException(
                    404,
                    "当前课程中不存在该知识点"
            );
        }

        // 3. 检查是否存在子知识点。
        if (hasPointReference(
                """
                SELECT COUNT(*)
                FROM course_knowledge_point
                WHERE parent_id = ?
                """,
                pointId
        )) {

            throw new BusinessException(
                    409,
                    "该知识点包含子知识点，请先调整子知识点的层级关系"
            );
        }

        // 4. 检查是否存在先修依赖。
        if (hasPointReference(
                """
                SELECT COUNT(*)
                FROM course_knowledge_dependency
                WHERE point_id = ?
                   OR prerequisite_id = ?
                """,
                pointId,
                pointId
        )) {

            throw new BusinessException(
                    409,
                    "该知识点存在先修依赖关系，请先解除关联"
            );
        }

        // 5. 检查是否存在课程资料关联。
        if (hasPointReference(
                """
                SELECT COUNT(*)
                FROM course_document_knowledge_point
                WHERE point_id = ?
                """,
                pointId
        )) {

            throw new BusinessException(
                    409,
                    "该知识点已关联课程资料，请先解除资料关联"
            );
        }

        // 6. 检查学生掌握程度记录。
        if (hasPointReference(
                """
                SELECT COUNT(*)
                FROM student_knowledge_mastery
                WHERE point_id = ?
                """,
                pointId
        )) {

            throw new BusinessException(
                    409,
                    "该知识点已存在学生掌握程度记录，不能直接删除"
            );
        }

        // 7. 检查学生学习路径。
        if (hasPointReference(
                """
                SELECT COUNT(*)
                FROM student_learning_path_item
                WHERE point_id = ?
                """,
                pointId
        )) {

            throw new BusinessException(
                    409,
                    "该知识点已被学生学习路径使用，不能直接删除"
            );
        }

        // 8. 检查历史问答统计。
        if (hasPointReference(
                """
                SELECT COUNT(*)
                FROM student_question_knowledge
                WHERE point_id = ?
                """,
                pointId
        )) {

            throw new BusinessException(
                    409,
                    "该知识点已存在历史问答记录，不能直接删除"
            );
        }

        // 9. 执行删除。
        try {

            int deleted = jdbc.update(
                    """
                    DELETE FROM course_knowledge_point
                    WHERE id = ?
                      AND course_id = ?
                    """,
                    pointId,
                    courseId
            );

            if (deleted != 1) {

                throw new BusinessException(
                        409,
                        "知识点删除失败，请刷新后重试"
                );
            }

        } catch (
                org.springframework.dao.DataIntegrityViolationException e
        ) {

            throw new BusinessException(
                    409,
                    "该知识点仍存在业务关联，暂时无法删除"
            );
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void updatePointKeywords(
            Long courseId,
            Long pointId,
            Long userId,
            String matchKeywords) {

        lockPointCourse(courseId, userId);

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

        lockPointCourse(courseId, userId);

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

    /**
     * 解除两个知识点之间的先修依赖关系。
     */
    @Transactional(rollbackFor = Exception.class)
    public void removeDependency(
            Long courseId,
            Long userId,
            Long pointId,
            Long prerequisiteId) {

        lockPointCourse(
                courseId,
                userId
        );

        if (pointId == null
                || prerequisiteId == null
                || pointId <= 0
                || prerequisiteId <= 0) {

            throw new BusinessException(
                    400,
                    "知识点ID不合法"
            );
        }

        // 两个知识点必须同属于当前课程。
        List<Long> ids = jdbc.queryForList(
                """
                SELECT id
                FROM course_knowledge_point
                WHERE course_id = ?
                  AND id IN (?, ?)
                ORDER BY id
                FOR UPDATE
                """,
                Long.class,
                courseId,
                pointId,
                prerequisiteId
        );

        if (ids.size() != 2) {

            throw new BusinessException(
                    400,
                    "先修关系必须属于当前课程"
            );
        }

        // 重复解除视为成功，保证接口幂等。
        jdbc.update(
                """
                DELETE FROM course_knowledge_dependency
                WHERE point_id = ?
                  AND prerequisite_id = ?
                """,
                pointId,
                prerequisiteId
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
