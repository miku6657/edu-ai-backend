package com.edu.ai.document.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import com.edu.ai.audit.service.ContentOperationLogService;
import com.edu.ai.common.BusinessException;

import com.edu.ai.course.service.CoursePermissionService;

import com.edu.ai.document.dto.DocumentQualityRequest;
import com.edu.ai.document.dto.DocumentQualityVO;

import com.edu.ai.document.entity.CourseDocument;
import com.edu.ai.document.mapper.CourseDocumentMapper;

import com.edu.ai.notification.service.NotificationService;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.ResultSet;
import java.sql.SQLException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class DocumentQualityService {

    private static final String IN_PROGRESS =
            "IN_PROGRESS";

    private static final String COMPLETED =
            "COMPLETED";

    private static final String PENDING =
            "PENDING";

    private static final String PASS =
            "PASS";

    private static final String FAIL =
            "FAIL";

    private static final String OPEN =
            "OPEN";

    private static final String RESOLVED =
            "RESOLVED";

    private static final String CLOSED =
            "CLOSED";

    private static final Set<String> ISSUE_TYPES =
            Set.of(
                    "MISSING_PAGE",
                    "WRONG_VERSION",
                    "DUPLICATE_CONTENT",
                    "PARSE_ERROR",
                    "WRONG_KNOWLEDGE_POINT",
                    "EXPIRED_DOCUMENT",
                    "OTHER"
            );

    private static final ZoneId APP_ZONE =
            ZoneId.of("Asia/Shanghai");

    private record AutoFinding(
            String issueType,
            String description,
            Long chunkId
    ) {}

    private record DuplicateCandidate(
            Long documentId,
            String documentName,
            long matchedCount,
            long totalCount,
            Long matchedChunkId
    ) {}

    private final JdbcTemplate jdbc;

    private final CoursePermissionService permissions;

    private final CourseDocumentMapper documentMapper;

    private final DocumentChunkService chunkService;

    private final NotificationService notificationService;

    private final ContentOperationLogService operationLogService;

    public DocumentQualityService(

            JdbcTemplate jdbc,

            CoursePermissionService permissions,

            CourseDocumentMapper documentMapper,

            DocumentChunkService chunkService,

            NotificationService notificationService,

            ContentOperationLogService operationLogService) {

        this.jdbc = jdbc;

        this.permissions = permissions;

        this.documentMapper = documentMapper;

        this.chunkService = chunkService;

        this.notificationService = notificationService;

        this.operationLogService = operationLogService;
    }

    // =================================================
    // 公共工具方法
    // =================================================

    private BusinessException badRequest(
            String message) {

        return new BusinessException(
                400,
                message
        );
    }

    private BusinessException conflict(
            String message) {

        return new BusinessException(
                409,
                message
        );
    }

    private BusinessException notFound(
            String message) {

        return new BusinessException(
                404,
                message
        );
    }

    private String normalize(
            String value) {

        return value == null
                ? ""
                : value.trim()
                       .toUpperCase(Locale.ROOT);
    }

    private String text(

            String value,

            int maxLength,

            boolean required,

            String fieldName) {

        String result = value == null
                ? ""
                : value.trim();

        if (required && result.isBlank()) {

            throw badRequest(
                    fieldName + "不能为空"
            );
        }

        if (result.length() > maxLength) {

            throw badRequest(
                    fieldName
                    + "不能超过"
                    + maxLength
                    + "个字符"
            );
        }

        return result.isBlank()
                ? null
                : result;
    }

    private long count(

            String sql,

            Object... args) {

        Long value = jdbc.queryForObject(

                sql,

                Long.class,

                args
        );

        return value == null
                ? 0L
                : value;
    }

    /**
     * 统一记录质量抽检任务下的操作。
     */
    private void logTaskOperation(
            Long courseId,
            Long taskId,
            Long userId,
            String operationType,
            String operationName,
            String description,
            String beforeValue,
            String afterValue) {

        List<Long> documentIds = jdbc.queryForList(
                """
                SELECT document_id
                FROM course_document_quality_task
                WHERE id = ?
                  AND course_id = ?
                """,
                Long.class,
                taskId,
                courseId
        );

        if (documentIds.isEmpty()) {
            throw new IllegalStateException(
                    "质量抽检任务不存在，无法写入统一运营日志"
            );
        }

        operationLogService.write(
                courseId,
                documentIds.get(0),
                userId,
                operationType,
                operationName,
                description,
                beforeValue,
                afterValue
        );
    }

    private String stringId(

            ResultSet rs,

            String column)

            throws SQLException {

        Object value = rs.getObject(column);

        return value == null
                ? null
                : value.toString();
    }

    private LocalDateTime dateTime(

            ResultSet rs,

            String column)

            throws SQLException {

        return rs.getObject(

                column,

                LocalDateTime.class
        );
    }

    private void requireUpdated(

            int updated,

            String message) {

        if (updated != 1) {

            throw conflict(message);
        }
    }

    // =================================================
    // 权限和数据库锁
    // =================================================

    /**
     * 仅管理员及课程所属教师有抽检管理权限。
     */
    private void requireManage(

            Long courseId,

            Long userId) {

        permissions.requireManage(

                courseId,

                userId
        );

        permissions.requireActiveRead(

                courseId,

                userId
        );
    }

    /**
     * 与知识点维护、发布审核使用相同的课程行锁。
     *
     * 必须在事务内部调用。
     */
    private void lockCourse(

            Long courseId,

            Long userId) {

        requireManage(

                courseId,

                userId
        );

        List<Integer> statuses =
                jdbc.queryForList(

                        """
                        SELECT status
                        FROM course
                        WHERE id = ?
                        FOR UPDATE
                        """,

                        Integer.class,

                        courseId
                );

        if (statuses.isEmpty()
                || !Integer.valueOf(1)
                           .equals(statuses.get(0))) {

            throw conflict(
                    "课程当前不可用"
            );
        }
    }

    /**
     * 获取文档行锁。
     *
     * 调用前必须已经获得课程行锁。
     */
    private CourseDocument lockQualityDocument(

            Long courseId,

            Long documentId,

            Long userId) {

        lockCourse(
                courseId,
                userId
        );

        CourseDocument document =
                documentMapper.selectOne(
                        new LambdaQueryWrapper<CourseDocument>()
                                .eq(
                                        CourseDocument::getId,
                                        documentId
                                )
                                .eq(
                                        CourseDocument::getCourseId,
                                        courseId
                                )
                                .last("LIMIT 1 FOR UPDATE")
                );

        if (document == null) {
            throw notFound("当前课程中不存在该资料");
        }

        if (!"READY".equals(document.getStatus())
                || !"CURRENT".equals(document.getVersionStatus())
                || !"APPROVED".equals(document.getReviewStatus())) {

            throw conflict("只有当前已正式发布的资料才能执行质量控制");
        }

        return document;
    }

    /**
     * 校验责任人。
     *
     * 当前系统没有独立资源管理员角色，
     * 因此责任人必须是管理员或课程所属教师。
     */
    private void requireAssignee(

            Long courseId,

            Long assigneeId) {

        if (assigneeId == null) {

            throw badRequest(
                    "请选择整改责任人"
            );
        }

        permissions.requireManage(

                courseId,

                assigneeId
        );
    }

    /**
     * 为质量问题责任人发送站内通知。
     */
    private void notifyIssueAssigned(

            Long issueId,

            Long assigneeId,

            String description) {

        notificationService.createIfAbsent(

                assigneeId,

                "DOC_QUALITY_ASSIGNED",

                issueId,

                "新的知识库质量整改任务",

                "你有一项待整改的知识库质量问题："
                        + description
                        + "。请前往对应课程知识库处理。"
        );
    }

    // =================================================
    // RowMapper
    // =================================================

    private final RowMapper<DocumentQualityVO.Task>
            taskMapper = (rs, rowNum) ->

            new DocumentQualityVO.Task(

                    stringId(rs, "id"),

                    stringId(rs, "course_id"),

                    stringId(rs, "document_id"),

                    stringId(rs, "resource_id"),

                    rs.getInt("version_no"),

                    rs.getString("document_name"),

                    rs.getString("title"),

                    rs.getInt("sample_rate"),

                    rs.getInt("total_chunks"),

                    rs.getInt("sample_count"),

                    rs.getString("status"),

                    stringId(rs, "creator_id"),

                    stringId(rs, "completed_by"),

                    dateTime(rs, "completed_time"),

                    dateTime(rs, "create_time"),

                    dateTime(rs, "update_time")
            );

    private final RowMapper<DocumentQualityVO.Sample>
            sampleMapper = (rs, rowNum) ->

            new DocumentQualityVO.Sample(

                    stringId(rs, "id"),

                    stringId(rs, "task_id"),

                    stringId(rs, "chunk_id"),

                    rs.getInt("page_no"),

                    rs.getInt("chunk_index"),

                    rs.getString("section_title"),

                    rs.getString("content"),

                    rs.getString("content_hash"),

                    rs.getInt("duplicate_count"),

                    rs.getString("status"),

                    rs.getString("check_comment"),

                    stringId(rs, "checker_id"),

                    dateTime(rs, "checked_time")
            );

    private final RowMapper<DocumentQualityVO.Issue>
            issueMapper = (rs, rowNum) ->

            new DocumentQualityVO.Issue(

                    stringId(rs, "id"),

                    stringId(rs, "task_id"),

                    stringId(rs, "sample_id"),

                    rs.getString("issue_type"),

                    rs.getString("description"),

                    stringId(rs, "reporter_id"),

                    stringId(rs, "assignee_id"),

                    rs.getString("status"),

                    rs.getString("resolution"),

                    stringId(
                            rs,
                            "replacement_document_id"
                    ),

                    dateTime(rs, "resolved_time"),

                    stringId(rs, "reviewer_id"),

                    rs.getString("review_comment"),

                    dateTime(rs, "reviewed_time"),

                    dateTime(rs, "create_time")
            );

    // =================================================
    // 查询及锁定业务对象
    // =================================================

    private DocumentQualityVO.Task requireTask(

            Long courseId,

            Long taskId,

            boolean lock) {

        String sql = """
                SELECT *
                FROM course_document_quality_task
                WHERE id = ?
                  AND course_id = ?
                """;

        if (lock) {

            sql += " FOR UPDATE";
        }

        List<DocumentQualityVO.Task> rows =
                jdbc.query(

                        sql,

                        taskMapper,

                        taskId,

                        courseId
                );

        if (rows.isEmpty()) {

            throw notFound(
                    "抽检任务不存在"
            );
        }

        return rows.get(0);
    }

    private DocumentQualityVO.Task lockActiveTask(

            Long courseId,

            Long taskId,

            Long userId) {

        lockCourse(

                courseId,

                userId
        );

        DocumentQualityVO.Task task =
                requireTask(

                        courseId,

                        taskId,

                        true
                );

        if (!IN_PROGRESS.equals(
                task.status())) {

            throw conflict(
                    "抽检任务已经结束，不允许继续修改"
            );
        }

        return task;
    }

    private DocumentQualityVO.Sample requireSample(

            Long taskId,

            Long sampleId) {

        List<DocumentQualityVO.Sample> rows =
                jdbc.query(

                        """
                        SELECT *
                        FROM course_document_quality_sample
                        WHERE id = ?
                          AND task_id = ?
                        FOR UPDATE
                        """,

                        sampleMapper,

                        sampleId,

                        taskId
                );

        if (rows.isEmpty()) {

            throw notFound(
                    "抽检样本不存在"
            );
        }

        return rows.get(0);
    }

    private DocumentQualityVO.Issue requireIssue(

            Long taskId,

            Long issueId) {

        List<DocumentQualityVO.Issue> rows =
                jdbc.query(

                        """
                        SELECT *
                        FROM course_document_quality_issue
                        WHERE id = ?
                          AND task_id = ?
                        FOR UPDATE
                        """,

                        issueMapper,

                        issueId,

                        taskId
                );

        if (rows.isEmpty()) {

            throw notFound(
                    "质量问题不存在"
            );
        }

        return rows.get(0);
    }

    // =================================================
    // 1. 查询责任人
    // =================================================

    @Transactional(readOnly = true)
    public List<DocumentQualityVO.Assignee>
            listAssignees(

            Long courseId,

            Long userId) {

        requireManage(

                courseId,

                userId
        );

        return jdbc.query(

                """
                SELECT DISTINCT
                    u.id,
                    u.username,
                    u.role
                FROM sys_user u
                JOIN course c
                  ON c.id = ?
                WHERE u.status = 1
                  AND (
                      u.role = 'ADMIN'
                      OR (
                          u.role = 'TEACHER'
                          AND u.id = c.user_id
                      )
                  )
                ORDER BY u.role, u.id
                """,

                (rs, rowNum) ->

                        new DocumentQualityVO.Assignee(

                                stringId(rs, "id"),

                                rs.getString("username"),

                                rs.getString("role")
                        ),

                courseId
        );
    }

    /**
     * 检查当前资料是否与同课程其他已发布资料
     * 存在高度重复的知识切片。
     *
     * 检测规则：
     * 1. 只比较当前生效且审核通过的其他资料。
     * 2. 只比较SHA-256完全一致的切片。
     * 3. 至少匹配2个不同切片。
     * 4. 匹配比例达到80%才标记疑似重复。
     * 5. 排除同一资源的历史版本。
     */
    private List<DuplicateCandidate> findDuplicateCandidates(
            Long courseId,
            Long documentId,
            Long resourceId) {

        return jdbc.query(
                """
                SELECT

                    other.document_id AS duplicate_document_id,

                    d.original_name AS duplicate_document_name,

                    COUNT(
                        DISTINCT target.content_hash
                    ) AS matched_count,

                    (
                        SELECT COUNT(DISTINCT source.content_hash)

                        FROM course_document_chunk source

                        WHERE source.course_id = ?

                          AND source.document_id = ?

                          AND source.content_hash IS NOT NULL
                    ) AS target_count,

                    MIN(target.id) AS matched_chunk_id

                FROM course_document_chunk target

                JOIN course_document_chunk other

                  ON other.course_id = target.course_id

                 AND other.document_id <> target.document_id

                 AND other.content_hash = target.content_hash

                JOIN course_document d

                  ON d.id = other.document_id

                 AND d.course_id = other.course_id

                WHERE target.course_id = ?

                  AND target.document_id = ?

                  AND target.content_hash IS NOT NULL

                  AND d.status = 'READY'

                  AND d.version_status = 'CURRENT'

                  AND d.review_status = 'APPROVED'

                  AND d.resource_id <> ?

                GROUP BY

                    other.document_id,

                    d.original_name

                HAVING COUNT(
                    DISTINCT target.content_hash
                ) >= 2

                AND COUNT(
                    DISTINCT target.content_hash
                ) >= CEIL(

                    (
                        SELECT COUNT(DISTINCT source.content_hash)

                        FROM course_document_chunk source

                        WHERE source.course_id = ?

                          AND source.document_id = ?

                          AND source.content_hash IS NOT NULL

                    ) * 0.8

                )

                ORDER BY matched_count DESC

                LIMIT 5
                """,

                (rs, rowNum) -> new DuplicateCandidate(

                        rs.getLong("duplicate_document_id"),

                        rs.getString("duplicate_document_name"),

                        rs.getLong("matched_count"),

                        rs.getLong("target_count"),

                        rs.getLong("matched_chunk_id")
                ),

                courseId,
                documentId,
                courseId,
                documentId,
                resourceId,
                courseId,
                documentId
        );
    }

    // =================================================
    // 2. 创建质量抽检任务
    // =================================================

    @Transactional(
            rollbackFor = Exception.class,
            isolation = Isolation.READ_COMMITTED
    )
    public DocumentQualityVO.Detail createTask(

            Long courseId,

            Long documentId,

            Long userId,

            DocumentQualityRequest.CreateTask request) {

        if (request == null) {

            throw badRequest(
                    "抽检参数不能为空"
            );
        }

        String title = text(

                request.title(),

                200,

                true,

                "抽检任务名称"
        );

        Integer sampleRate =
                request.sampleRate();

        if (sampleRate == null
                || sampleRate < 1
                || sampleRate > 100) {

            throw badRequest(
                    "抽检比例必须在1至100之间"
            );
        }

        lockCourse(

                courseId,

                userId
        );

        CourseDocument document =
                chunkService.requireManagedDocument(

                        courseId,

                        documentId,

                        userId
                );

        // 锁定文档并读取最新状态。
        List<Map<String, Object>> documentStates =
                jdbc.queryForList(
                        """
                        SELECT
                            status,
                            version_status,
                            review_status
                        FROM course_document
                        WHERE id = ?
                          AND course_id = ?
                        FOR UPDATE
                        """,
                        documentId,
                        courseId
                );

        if (documentStates.isEmpty()) {
            throw notFound("抽检文档不存在");
        }

        Map<String, Object> state =
                documentStates.get(0);

        if (!"READY".equals(state.get("status"))) {
            throw conflict("文档尚未完成解析");
        }

        String versionStatus =
                Objects.toString(
                        state.get("version_status"),
                        ""
                );

        if (!"PENDING".equals(versionStatus)
                && !"CURRENT".equals(versionStatus)) {

            throw conflict("历史版本不能创建新的抽检任务");
        }

        if ("PENDING".equals(state.get("review_status"))) {
            throw conflict("资料正在等待发布审核，请先撤回审核");
        }

        if (document.getResourceId() == null
                || document.getVersionNo() == null) {

            throw conflict(
                    "文档版本尚未完成初始化"
            );
        }

        // 同一文档最多存在一个进行中的抽检任务。
        long activeTasks = count(

                """
                SELECT COUNT(*)
                FROM course_document_quality_task
                WHERE course_id = ?
                  AND document_id = ?
                  AND status = 'IN_PROGRESS'
                """,

                courseId,

                documentId
        );

        if (activeTasks > 0) {

            throw conflict(
                    "当前文档已经存在进行中的抽检任务"
            );
        }

        long totalChunks = count(

                """
                SELECT COUNT(*)
                FROM course_document_chunk
                WHERE course_id = ?
                  AND document_id = ?
                """,

                courseId,

                documentId
        );

        if (totalChunks == 0) {

            throw conflict(
                    "当前资料暂无知识切片，无法抽检"
            );
        }

        if (totalChunks > Integer.MAX_VALUE) {

            throw conflict(
                    "当前文档切片数量超出支持范围"
            );
        }

        // 向上取整，保证只要有切片就至少抽取一个。
        int sampleCount = Math.toIntExact(

                (totalChunks * sampleRate + 99) / 100
        );

        KeyHolder keyHolder =
                new GeneratedKeyHolder();

        jdbc.update(

                connection -> {

                    PreparedStatement ps =
                            connection.prepareStatement(

                                    """
                                    INSERT INTO
                                    course_document_quality_task (

                                        course_id,
                                        document_id,
                                        resource_id,
                                        version_no,
                                        document_name,
                                        title,
                                        sample_rate,
                                        total_chunks,
                                        sample_count,
                                        status,
                                        creator_id

                                    )
                                    VALUES (
                                        ?, ?, ?, ?, ?,
                                        ?, ?, ?, ?,
                                        'IN_PROGRESS',
                                        ?
                                    )
                                    """,

                                    Statement.RETURN_GENERATED_KEYS
                            );

                    ps.setLong(
                            1,
                            courseId
                    );

                    ps.setLong(
                            2,
                            documentId
                    );

                    ps.setLong(
                            3,
                            document.getResourceId()
                    );

                    ps.setInt(
                            4,
                            document.getVersionNo()
                    );

                    ps.setString(
                            5,
                            document.getOriginalName()
                    );

                    ps.setString(
                            6,
                            title
                    );

                    ps.setInt(
                            7,
                            sampleRate
                    );

                    ps.setInt(
                            8,
                            (int) totalChunks
                    );

                    ps.setInt(
                            9,
                            sampleCount
                    );

                    ps.setLong(
                            10,
                            userId
                    );

                    return ps;
                },

                keyHolder
        );

        Number key = keyHolder.getKey();

        if (key == null) {

            throw new BusinessException(
                    500,
                    "创建抽检任务失败"
            );
        }

        Long taskId = key.longValue();

        // 随机抽取切片并保存正文快照。
        int inserted = jdbc.update(

                """
                INSERT INTO
                course_document_quality_sample (

                    task_id,
                    chunk_id,
                    page_no,
                    chunk_index,
                    section_title,
                    content,
                    content_hash,
                    duplicate_count,
                    status

                )
                SELECT

                    ?,
                    c.id,
                    c.page_no,
                    c.chunk_index,
                    c.section_title,
                    c.content,
                    c.content_hash,
                    COALESCE(d.duplicate_count, 0),
                    'PENDING'

                FROM course_document_chunk c

                LEFT JOIN (

                    SELECT
                        content_hash,
                        COUNT(*) AS duplicate_count

                    FROM course_document_chunk

                    WHERE course_id = ?
                      AND document_id = ?
                      AND content_hash IS NOT NULL

                    GROUP BY content_hash

                ) d
                  ON d.content_hash = c.content_hash

                WHERE c.course_id = ?
                  AND c.document_id = ?

                ORDER BY RAND()

                LIMIT ?
                """,

                taskId,

                courseId,

                documentId,

                courseId,

                documentId,

                sampleCount
        );

        if (inserted != sampleCount) {

            throw conflict(
                    "抽检期间知识切片数量发生变化，请重试"
            );
        }

        logTaskOperation(
                courseId,
                taskId,
                userId,
                "QUALITY_TASK_CREATE",
                "创建资料质量抽检任务",
                "创建抽检任务《" + title
                        + "》，抽检比例=" + sampleRate
                        + "%，抽检样本数=" + sampleCount,
                null,
                "taskId=" + taskId
                        + "，status=IN_PROGRESS"
        );

        return getDetail(

                courseId,

                taskId,

                userId
        );
    }

    /**
     * 创建自动质量检查任务。
     *
     * 自动检查内容：
     * 1. PDF页面无可提取文本。
     * 2. PDF页面解析异常。
     * 3. 与同课程其他资料高度重复。
     * 4. 资料已经超过有效期。
     *
     * 自动检查结果直接进入现有质量整改流程。
     */
    @Transactional(
            rollbackFor = Exception.class,
            isolation = Isolation.READ_COMMITTED
    )
    public DocumentQualityVO.Detail createAutomaticScanTask(

            Long courseId,

            Long documentId,

            Long userId,

            PdfQualityInspectionClient.InspectionResult inspection) {

        if (inspection == null || inspection.pageCount() <= 0) {
            throw badRequest("PDF检查结果无效");
        }

        // 锁定课程和资料，避免与发布、删除操作并发。
        CourseDocument document =
                lockQualityDocument(
                        courseId,
                        documentId,
                        userId
                );

        // 当前资料不能同时存在两个未完成抽检任务。
        long activeTasks = count(
                """
                SELECT COUNT(*)

                FROM course_document_quality_task

                WHERE course_id = ?

                  AND document_id = ?

                  AND status = 'IN_PROGRESS'
                """,
                courseId,
                documentId
        );

        if (activeTasks > 0) {
            throw conflict("当前资料已有进行中的质量检查任务");
        }

        // 同一资料版本只允许建立一次自动质量检查任务。
        long sameVersionTasks = count(
                """
                SELECT COUNT(*)
                FROM course_document_quality_task
                WHERE course_id = ?
                  AND document_id = ?
                  AND version_no = ?
                """,
                courseId,
                documentId,
                document.getVersionNo()
        );

        if (sameVersionTasks > 0) {
            throw conflict(
                    "当前资料版本已经执行过质量检查，无需重复创建任务"
            );
        }

        List<Map<String, Object>> chunks =
                jdbc.queryForList(
                        """
                        SELECT
                            id,
                            page_no,
                            chunk_index,
                            content_hash

                        FROM course_document_chunk

                        WHERE course_id = ?

                          AND document_id = ?

                        ORDER BY page_no, chunk_index
                        """,
                        courseId,
                        documentId
                );

        if (chunks.isEmpty()) {
            throw conflict("当前资料没有知识切片，无法执行自动质量检查");
        }

        if (chunks.size() > Integer.MAX_VALUE) {
            throw conflict("知识切片数量超出支持范围");
        }

        int totalChunks = chunks.size();

        Long firstChunkId = ((Number) chunks.get(0).get("id"))
                .longValue();

        List<AutoFinding> findings = new ArrayList<>();

        // ---------------------------------------------
        // 1. 检查没有提取到文本的页面
        // ---------------------------------------------

        List<Integer> emptyPages =
                inspection.pagesWithoutText();

        if (emptyPages != null && !emptyPages.isEmpty()) {

            String imagePages =
                    inspection.imageOnlyPages() == null
                            ? "无"
                            : inspection.imageOnlyPages().toString();

            findings.add(
                    new AutoFinding(
                            "MISSING_PAGE",
                            "PDF共" + inspection.pageCount()
                                    + "页，其中以下页面未提取到可检索文本："
                                    + emptyPages
                                    + "。其中图片页面：" + imagePages
                                    + "。可能为空白页、扫描图片或文本解析失败，"
                                    + "请人工核对PDF原文。",
                            firstChunkId
                    )
            );
        }

        // ---------------------------------------------
        // 2. 检查页面解析异常
        // ---------------------------------------------

        List<Integer> parseErrorPages =
                inspection.parseErrorPages();

        if (parseErrorPages != null
                && !parseErrorPages.isEmpty()) {

            findings.add(
                    new AutoFinding(
                            "PARSE_ERROR",
                            "PDF以下页面发生文本解析异常："
                                    + parseErrorPages
                                    + "，请检查原始PDF文件。",
                            firstChunkId
                    )
            );
        }

        // ---------------------------------------------
        // 3. 检查同课程其他资料中的重复内容
        // ---------------------------------------------

        List<DuplicateCandidate> duplicates =
                findDuplicateCandidates(
                        courseId,
                        documentId,
                        document.getResourceId()
                );

        for (DuplicateCandidate duplicate : duplicates) {

            double ratio =
                    duplicate.totalCount() == 0
                            ? 0
                            : duplicate.matchedCount() * 100.0
                                    / duplicate.totalCount();

            findings.add(
                    new AutoFinding(
                            "DUPLICATE_CONTENT",
                            "当前资料与《"
                                    + duplicate.documentName()
                                    + "》存在高度重复内容，"
                                    + "匹配切片数："
                                    + duplicate.matchedCount()
                                    + "，当前资料有效切片数："
                                    + duplicate.totalCount()
                                    + "，匹配比例："
                                    + String.format(
                                            Locale.ROOT,
                                            "%.1f%%",
                                            ratio
                                    )
                                    + "。请人工确认是否属于重复资料。",
                            duplicate.matchedChunkId()
                    )
            );
        }

        // ---------------------------------------------
        // 4. 检查资料有效期
        // ---------------------------------------------

        LocalDate today =
                LocalDate.now(APP_ZONE);

        if (document.getValidUntil() != null
                && document.getValidUntil().isBefore(today)) {

            findings.add(
                    new AutoFinding(
                            "EXPIRED_DOCUMENT",
                            "资料《"
                                    + document.getOriginalName()
                                    + "》已经超过有效期，截止日期："
                                    + document.getValidUntil()
                                    + "，请确认是否需要更新或重新发布。",
                            firstChunkId
                    )
            );
        }

        // ---------------------------------------------
        // 5. 确定整改责任人
        // ---------------------------------------------

        List<Long> ownerIds = jdbc.queryForList(
                """
                SELECT c.user_id

                FROM course c

                JOIN sys_user u
                  ON u.id = c.user_id

                WHERE c.id = ?

                  AND u.status = 1

                  AND u.role = 'TEACHER'

                LIMIT 1
                """,
                Long.class,
                courseId
        );

        Long assigneeId = ownerIds.isEmpty()
                ? userId
                : ownerIds.get(0);

        // ---------------------------------------------
        // 6. 创建自动质量任务
        // ---------------------------------------------

        String taskTitle =
                "自动质量检查-" + LocalDateTime.now(APP_ZONE);

        KeyHolder taskKeyHolder =
                new GeneratedKeyHolder();

        jdbc.update(
                connection -> {

                    PreparedStatement ps =
                            connection.prepareStatement(
                                    """
                                    INSERT INTO course_document_quality_task (

                                        course_id,
                                        document_id,
                                        resource_id,
                                        version_no,
                                        document_name,
                                        title,
                                        sample_rate,
                                        total_chunks,
                                        sample_count,
                                        status,
                                        creator_id

                                    )
                                    VALUES (
                                        ?, ?, ?, ?, ?,
                                        ?, 100, ?, ?,
                                        'IN_PROGRESS',
                                        ?
                                    )
                                    """,
                                    Statement.RETURN_GENERATED_KEYS
                            );

                    ps.setLong(1, courseId);
                    ps.setLong(2, documentId);
                    ps.setLong(3, document.getResourceId());
                    ps.setInt(4, document.getVersionNo());
                    ps.setString(5, document.getOriginalName());
                    ps.setString(6, taskTitle);
                    ps.setInt(7, totalChunks);
                    ps.setInt(8, totalChunks);
                    ps.setLong(9, userId);

                    return ps;
                },
                taskKeyHolder
        );

        Number taskKey = taskKeyHolder.getKey();

        if (taskKey == null) {
            throw new BusinessException(500, "自动质量检查任务创建失败");
        }

        Long taskId = taskKey.longValue();

        // ---------------------------------------------
        // 7. 将全部知识切片写入样本
        // ---------------------------------------------

        jdbc.update(
                """
                INSERT INTO course_document_quality_sample (

                    task_id,
                    chunk_id,
                    page_no,
                    chunk_index,
                    section_title,
                    content,
                    content_hash,
                    duplicate_count,
                    status,
                    check_comment,
                    checker_id,
                    checked_time

                )

                SELECT

                    ?,
                    c.id,
                    c.page_no,
                    c.chunk_index,
                    c.section_title,
                    c.content,
                    c.content_hash,
                    COALESCE(d.duplicate_count, 1),

                    'PASS',

                    '自动质量检查未发现该切片的异常',

                    ?,

                    NOW()

                FROM course_document_chunk c

                LEFT JOIN (

                    SELECT
                        content_hash,
                        COUNT(*) AS duplicate_count

                    FROM course_document_chunk

                    WHERE course_id = ?

                      AND document_id = ?

                      AND content_hash IS NOT NULL

                    GROUP BY content_hash

                ) d

                  ON d.content_hash = c.content_hash

                WHERE c.course_id = ?

                  AND c.document_id = ?
                """,

                taskId,
                userId,
                courseId,
                documentId,
                courseId,
                documentId
        );

        // ---------------------------------------------
        // 8. 获取样本ID与切片ID的对应关系
        // ---------------------------------------------

        List<Map<String, Object>> sampleRows =
                jdbc.queryForList(
                        """
                        SELECT id, chunk_id

                        FROM course_document_quality_sample

                        WHERE task_id = ?
                        """,
                        taskId
                );

        Map<Long, Long> sampleByChunk =
                new HashMap<>();

        for (Map<String, Object> row : sampleRows) {

            Long sampleId =
                    ((Number) row.get("id")).longValue();

            Long chunkId =
                    ((Number) row.get("chunk_id")).longValue();

            sampleByChunk.put(chunkId, sampleId);
        }

        if (sampleByChunk.size() != totalChunks) {
            throw conflict("自动质量检查样本创建不完整");
        }

        // ---------------------------------------------
        // 9. 创建质量问题
        // ---------------------------------------------

        Set<Long> failedChunkIds = new HashSet<>();

        for (AutoFinding finding : findings) {

            Long chunkId = finding.chunkId();

            Long sampleId = sampleByChunk.get(chunkId);

            if (sampleId == null) {
                throw conflict("自动检查问题无法关联知识切片");
            }

            failedChunkIds.add(chunkId);

            KeyHolder issueKeyHolder =
                    new GeneratedKeyHolder();

            jdbc.update(
                    connection -> {

                        PreparedStatement ps =
                                connection.prepareStatement(
                                        """
                                        INSERT INTO course_document_quality_issue (

                                            task_id,
                                            sample_id,
                                            issue_type,
                                            description,
                                            reporter_id,
                                            assignee_id,
                                            status

                                        )
                                        VALUES (?, ?, ?, ?, ?, ?, 'OPEN')
                                        """,
                                        Statement.RETURN_GENERATED_KEYS
                                );

                        ps.setLong(1, taskId);
                        ps.setLong(2, sampleId);
                        ps.setString(3, finding.issueType());
                        ps.setString(4, finding.description());
                        ps.setLong(5, userId);
                        ps.setLong(6, assigneeId);

                        return ps;
                    },
                    issueKeyHolder
            );

            Number issueKey = issueKeyHolder.getKey();

            if (issueKey != null) {

                notifyIssueAssigned(
                        issueKey.longValue(),
                        assigneeId,
                        finding.description()
                );
            }
        }

        // ---------------------------------------------
        // 10. 将异常切片标记为FAIL
        // ---------------------------------------------

        for (Long chunkId : failedChunkIds) {

            jdbc.update(
                    """
                    UPDATE course_document_quality_sample

                    SET status = 'FAIL',

                        check_comment = '自动质量检查发现问题，请查看整改清单'

                    WHERE task_id = ?

                      AND chunk_id = ?
                    """,
                    taskId,
                    chunkId
            );
        }

        // ---------------------------------------------
        // 11. 没有发现问题则自动完成任务
        // ---------------------------------------------

        if (findings.isEmpty()) {

            jdbc.update(
                    """
                    UPDATE course_document_quality_task

                    SET status = 'COMPLETED',

                        completed_by = ?,

                        completed_time = NOW(),

                        update_time = NOW()

                    WHERE id = ?

                      AND course_id = ?

                      AND status = 'IN_PROGRESS'
                    """,
                    userId,
                    taskId,
                    courseId
            );
        }

        // ---------------------------------------------
        // 12. 写入运营日志
        // ---------------------------------------------

        logTaskOperation(
                courseId,
                taskId,
                userId,
                "QUALITY_AUTO_SCAN",
                "执行资料自动质量检查",
                "资料《" + document.getOriginalName()
                        + "》自动检查完成。PDF页数="
                        + inspection.pageCount()
                        + "，知识切片="
                        + totalChunks
                        + "，发现问题="
                        + findings.size(),
                null,
                "taskId=" + taskId
                        + "，issues=" + findings.size()
                        + "，status="
                        + (findings.isEmpty()
                            ? COMPLETED
                            : IN_PROGRESS)
        );

        return getDetail(
                courseId,
                taskId,
                userId
        );
    }

    // =================================================
    // 3. 查询抽检任务列表
    // =================================================

    @Transactional(readOnly = true)
    public List<DocumentQualityVO.Task> listTasks(

            Long courseId,

            Long documentId,

            Long userId) {

        requireManage(

                courseId,

                userId
        );

        if (documentId != null) {

            return jdbc.query(

                    """
                    SELECT *
                    FROM course_document_quality_task
                    WHERE course_id = ?
                      AND document_id = ?
                    ORDER BY id DESC
                    LIMIT 100
                    """,

                    taskMapper,

                    courseId,

                    documentId
            );
        }

        return jdbc.query(

                """
                SELECT *
                FROM course_document_quality_task
                WHERE course_id = ?
                ORDER BY id DESC
                LIMIT 100
                """,

                taskMapper,

                courseId
        );
    }

    // =================================================
    // 4. 查询抽检任务详情
    // =================================================

    @Transactional(readOnly = true)
    public DocumentQualityVO.Detail getDetail(

            Long courseId,

            Long taskId,

            Long userId) {

        requireManage(

                courseId,

                userId
        );

        DocumentQualityVO.Task task =
                requireTask(

                        courseId,

                        taskId,

                        false
                );

        List<DocumentQualityVO.Sample> samples =
                jdbc.query(

                        """
                        SELECT *
                        FROM course_document_quality_sample
                        WHERE task_id = ?
                        ORDER BY
                            page_no,
                            chunk_index,
                            id
                        """,

                        sampleMapper,

                        taskId
                );

        List<DocumentQualityVO.Issue> issues =
                jdbc.query(

                        """
                        SELECT *
                        FROM course_document_quality_issue
                        WHERE task_id = ?
                        ORDER BY id DESC
                        """,

                        issueMapper,

                        taskId
                );

        DocumentQualityVO.Progress progress =
                calculateProgress(

                        task,

                        samples,

                        issues
                );

        return new DocumentQualityVO.Detail(

                task,

                progress,

                samples,

                issues
        );
    }

    /**
     * 计算抽检任务进度。
     */
    private DocumentQualityVO.Progress calculateProgress(

            DocumentQualityVO.Task task,

            List<DocumentQualityVO.Sample> samples,

            List<DocumentQualityVO.Issue> issues) {

        int total = samples.size();

        int pending = Math.toIntExact(

                samples.stream()

                        .filter(sample ->
                                PENDING.equals(
                                        sample.status()
                                )
                        )

                        .count()
        );

        int passed = Math.toIntExact(

                samples.stream()

                        .filter(sample ->
                                PASS.equals(
                                        sample.status()
                                )
                        )

                        .count()
        );

        int failed = Math.toIntExact(

                samples.stream()

                        .filter(sample ->
                                FAIL.equals(
                                        sample.status()
                                )
                        )

                        .count()
        );

        int checked = passed + failed;

        int percent = total == 0

                ? 0

                : (int) (
                        checked * 100L / total
                  );

        // 合格率以已经检查的样本为分母。
        double passRate = checked == 0

                ? 0.0

                : Math.round(
                        passed * 10000.0 / checked
                  ) / 100.0;

        int open = Math.toIntExact(

                issues.stream()

                        .filter(issue ->
                                OPEN.equals(
                                        issue.status()
                                )
                        )

                        .count()
        );

        int resolved = Math.toIntExact(

                issues.stream()

                        .filter(issue ->
                                RESOLVED.equals(
                                        issue.status()
                                )
                        )

                        .count()
        );

        int closed = Math.toIntExact(

                issues.stream()

                        .filter(issue ->
                                CLOSED.equals(
                                        issue.status()
                                )
                        )

                        .count()
        );

        boolean canComplete =

                IN_PROGRESS.equals(task.status())

                && total > 0

                && total == task.sampleCount()

                && checked == total

                && pending == 0

                && closed == issues.size();

        return new DocumentQualityVO.Progress(

                total,

                pending,

                passed,

                failed,

                checked,

                percent,

                passRate,

                issues.size(),

                open,

                resolved,

                closed,

                canComplete
        );
    }

    // =================================================
    // 5. 保存样本检查结果
    // =================================================

    @Transactional(
            rollbackFor = Exception.class,
            isolation = Isolation.READ_COMMITTED
    )
    public DocumentQualityVO.Detail checkSample(

            Long courseId,

            Long taskId,

            Long sampleId,

            Long userId,

            DocumentQualityRequest.CheckSample request) {

        if (request == null) {

            throw badRequest(
                    "检查结果不能为空"
            );
        }

        String result = normalize(
                request.result()
        );

        if (!PASS.equals(result)
                && !FAIL.equals(result)) {

            throw badRequest(
                    "检查结果必须为PASS或FAIL"
            );
        }

        String comment = text(

                request.comment(),

                1000,

                false,

                "检查说明"
        );

        List<DocumentQualityRequest.IssueInput> issues =

                request.issues() == null

                        ? List.of()

                        : request.issues();

        if (PASS.equals(result)
                && !issues.isEmpty()) {

            throw badRequest(
                    "合格样本不能同时提交质量问题"
            );
        }

        if (FAIL.equals(result)
                && issues.isEmpty()) {

            throw badRequest(
                    "不合格样本至少需要登记一个质量问题"
            );
        }

        if (issues.size() > 10) {

            throw badRequest(
                    "单个样本最多登记10个质量问题"
            );
        }

        lockActiveTask(

                courseId,

                taskId,

                userId
        );

        DocumentQualityVO.Sample sample =
                requireSample(

                        taskId,

                        sampleId
                );

        if (!PENDING.equals(
                sample.status())) {

            throw conflict(
                    "该样本已经完成检查"
            );
        }

        List<Object[]> args =
                new ArrayList<>();

        for (DocumentQualityRequest.IssueInput issue
                : issues) {

            if (issue == null) {

                throw badRequest(
                        "质量问题不能为空"
                );
            }

            String issueType =
                    normalize(issue.issueType());

            if (!ISSUE_TYPES.contains(
                    issueType)) {

                throw badRequest(
                        "不支持的质量问题类型"
                );
            }

            String description = text(

                    issue.description(),

                    2000,

                    true,

                    "质量问题描述"
            );

            requireAssignee(

                    courseId,

                    issue.assigneeId()
            );

            args.add(

                    new Object[] {

                            taskId,

                            sampleId,

                            issueType,

                            description,

                            userId,

                            issue.assigneeId()
                    }
            );
        }

        if (!args.isEmpty()) {

            jdbc.batchUpdate(

                    """
                    INSERT INTO
                    course_document_quality_issue (

                        task_id,
                        sample_id,
                        issue_type,
                        description,
                        reporter_id,
                        assignee_id,
                        status

                    )
                    VALUES (
                        ?, ?, ?, ?, ?, ?,
                        'OPEN'
                    )
                    """,

                    args
            );
        }

        int updated = jdbc.update(

                """
                UPDATE course_document_quality_sample

                SET status = ?,
                    check_comment = ?,
                    checker_id = ?,
                    checked_time = NOW()

                WHERE id = ?
                  AND task_id = ?
                  AND status = 'PENDING'
                """,

                result,

                comment,

                userId,

                sampleId,

                taskId
        );

        requireUpdated(

                updated,

                "抽检样本状态已经变化"
        );

        // 查询本次新创建的质量问题并通知责任人。
        if (FAIL.equals(result)) {

            List<DocumentQualityVO.Issue> createdIssues =
                    jdbc.query(
                            """
                            SELECT *
                            FROM course_document_quality_issue
                            WHERE task_id = ?
                              AND sample_id = ?
                            """,
                            issueMapper,
                            taskId,
                            sampleId
                    );

            for (DocumentQualityVO.Issue issue
                    : createdIssues) {

                notifyIssueAssigned(

                        Long.valueOf(issue.id()),

                        Long.valueOf(issue.assigneeId()),

                        issue.description()
                );
            }
        }

        logTaskOperation(
                courseId,
                taskId,
                userId,
                "QUALITY_SAMPLE_CHECK",
                "检查资料质量样本",
                "检查样本ID=" + sampleId
                        + "，检查结果=" + result
                        + (comment == null
                            ? ""
                            : "，检查意见=" + comment),
                "PENDING",
                result
        );

        return getDetail(

                courseId,

                taskId,

                userId
        );
    }

    // =================================================
    // 6. 重新分配责任人
    // =================================================

    @Transactional(
            rollbackFor = Exception.class,
            isolation = Isolation.READ_COMMITTED
    )
    public DocumentQualityVO.Detail assignIssue(

            Long courseId,

            Long taskId,

            Long issueId,

            Long userId,

            Long assigneeId) {

        lockActiveTask(

                courseId,

                taskId,

                userId
        );

        requireAssignee(

                courseId,

                assigneeId
        );

        DocumentQualityVO.Issue issue =
                requireIssue(

                        taskId,

                        issueId
                );

        if (!OPEN.equals(issue.status())) {

            throw conflict(
                    "只有待整改问题允许重新分配责任人"
            );
        }

        requireUpdated(

                jdbc.update(

                        """
                        UPDATE course_document_quality_issue

                        SET assignee_id = ?,
                            update_time = NOW()

                        WHERE id = ?
                          AND task_id = ?
                          AND status = 'OPEN'
                        """,

                        assigneeId,

                        issueId,

                        taskId
                ),

                "质量问题状态已经变化"
        );

        notifyIssueAssigned(

                issueId,

                assigneeId,

                issue.description()
        );

        logTaskOperation(
                courseId,
                taskId,
                userId,
                "QUALITY_ISSUE_ASSIGN",
                "调整质量整改责任人",
                "质量问题ID=" + issueId
                        + "，重新分配整改责任人",
                "assigneeId=" + issue.assigneeId(),
                "assigneeId=" + assigneeId
        );

        return getDetail(

                courseId,

                taskId,

                userId
        );
    }

    // =================================================
    // 7. 提交整改结果
    // =================================================

    @Transactional(
            rollbackFor = Exception.class,
            isolation = Isolation.READ_COMMITTED
    )
    public DocumentQualityVO.Detail resolveIssue(

            Long courseId,

            Long taskId,

            Long issueId,

            Long userId,

            DocumentQualityRequest.ResolveIssue request) {

        if (request == null) {

            throw badRequest(
                    "整改信息不能为空"
            );
        }

        String resolution = text(

                request.resolution(),

                2000,

                true,

                "整改说明"
        );

        DocumentQualityVO.Task task =
                lockActiveTask(

                        courseId,

                        taskId,

                        userId
                );

        DocumentQualityVO.Issue issue =
                requireIssue(

                        taskId,

                        issueId
                );

        if (!OPEN.equals(issue.status())) {

            throw conflict(
                    "只有待整改问题允许提交整改结果"
            );
        }

        if (!userId.toString().equals(
                issue.assigneeId())) {

            throw new BusinessException(

                    403,

                    "只有当前整改责任人可以提交整改结果"
            );
        }

        Long replacementId =
                request.replacementDocumentId();

        if (replacementId != null) {

            requireReplacementDocument(

                    courseId,

                    task,

                    replacementId
            );
        }

        requireUpdated(

                jdbc.update(

                        """
                        UPDATE course_document_quality_issue

                        SET status = 'RESOLVED',
                            resolution = ?,
                            replacement_document_id = ?,
                            resolved_time = NOW(),
                            reviewer_id = NULL,
                            review_comment = NULL,
                            reviewed_time = NULL,
                            update_time = NOW()

                        WHERE id = ?
                          AND task_id = ?
                          AND status = 'OPEN'
                        """,

                        resolution,

                        replacementId,

                        issueId,

                        taskId
                ),

                "提交整改失败，问题状态已经变化"
        );

        notificationService.createIfAbsent(

                Long.valueOf(issue.reporterId()),

                "DOC_QUALITY_RESOLVED",

                issueId,

                "知识库质量问题等待复核",

                "相关责任人已提交整改结果："
                        + issue.description()
                        + "。请前往知识库进行复核。"
        );

        logTaskOperation(
                courseId,
                taskId,
                userId,
                "QUALITY_ISSUE_RESOLVE",
                "提交质量问题整改",
                "质量问题ID=" + issueId
                        + "，整改说明=" + resolution
                        + (replacementId == null
                            ? ""
                            : "，整改新版本文档ID=" + replacementId),
                OPEN,
                RESOLVED
        );

        return getDetail(

                courseId,

                taskId,

                userId
        );
    }

    /**
     * 校验整改后的资源版本。
     */
    private void requireReplacementDocument(

            Long courseId,

            DocumentQualityVO.Task task,

            Long replacementId) {

        CourseDocument document =
                documentMapper.selectById(
                        replacementId
                );

        if (document == null
                || !Objects.equals(
                        document.getCourseId(),
                        courseId
                )) {

            throw badRequest(
                    "整改资源不属于当前课程"
            );
        }

        if (document.getResourceId() == null
                || !document.getResourceId()
                            .toString()
                            .equals(task.resourceId())) {

            throw badRequest(
                    "整改资源必须属于原来的资源版本链"
            );
        }

        if (document.getVersionNo() == null
                || document.getVersionNo()
                           <= task.versionNo()) {

            throw badRequest(
                    "整改资源必须是更新的资源版本"
            );
        }

        if (!"READY".equals(
                document.getStatus())) {

            throw conflict(
                    "整改资源尚未完成解析"
            );
        }
    }

    // =================================================
    // 8. 复核质量问题
    // =================================================

    @Transactional(
            rollbackFor = Exception.class,
            isolation = Isolation.READ_COMMITTED
    )
    public DocumentQualityVO.Detail reviewIssue(

            Long courseId,

            Long taskId,

            Long issueId,

            Long userId,

            DocumentQualityRequest.ReviewIssue request) {

        if (request == null) {

            throw badRequest(
                    "复核参数不能为空"
            );
        }

        String decision = normalize(
                request.decision()
        );

        if (!"APPROVE".equals(decision)
                && !"REJECT".equals(decision)) {

            throw badRequest(
                    "复核决定必须为APPROVE或REJECT"
            );
        }

        String comment = text(

                request.comment(),

                1000,

                "REJECT".equals(decision),

                "复核说明"
        );

        DocumentQualityVO.Task task =
                lockActiveTask(

                        courseId,

                        taskId,

                        userId
                );

        DocumentQualityVO.Issue issue =
                requireIssue(

                        taskId,

                        issueId
                );

        if (!RESOLVED.equals(issue.status())) {

            throw conflict(
                    "质量问题尚未提交整改"
            );
        }

        String nextStatus;

        if ("APPROVE".equals(decision)) {

            if (issue.replacementDocumentId()
                    != null) {

                requireReplacementDocument(

                        courseId,

                        task,

                        Long.valueOf(
                                issue.replacementDocumentId()
                        )
                );
            }

            nextStatus = CLOSED;

        } else {

            nextStatus = OPEN;
        }

        requireUpdated(

                jdbc.update(

                        """
                        UPDATE course_document_quality_issue

                        SET status = ?,
                            reviewer_id = ?,
                            review_comment = ?,
                            reviewed_time = NOW(),
                            update_time = NOW()

                        WHERE id = ?
                          AND task_id = ?
                          AND status = 'RESOLVED'
                        """,

                        nextStatus,

                        userId,

                        comment,

                        issueId,

                        taskId
                ),

                "复核失败，问题状态已经变化"
        );

        if ("APPROVE".equals(decision)) {

            notificationService.createIfAbsent(

                    Long.valueOf(issue.assigneeId()),

                    "DOC_QUALITY_CLOSED",

                    issueId,

                    "知识库质量问题已关闭",

                    "你提交的质量整改结果已通过复核。"
            );

        } else {

            notificationService.createIfAbsent(

                    Long.valueOf(issue.assigneeId()),

                    "DOC_QUALITY_REJECTED",

                    issueId,

                    "知识库质量整改未通过",

                    "整改结果未通过复核，原因："
                            + comment
            );
        }

        logTaskOperation(
                courseId,
                taskId,
                userId,
                "QUALITY_ISSUE_REVIEW",
                "复核质量整改结果",
                "复核质量问题ID=" + issueId
                        + "，决定=" + decision
                        + (comment == null
                            ? ""
                            : "，复核意见=" + comment),
                RESOLVED,
                nextStatus
        );

        return getDetail(

                courseId,

                taskId,

                userId
        );
    }

    // =================================================
    // 9. 完成抽检任务
    // =================================================

    @Transactional(
            rollbackFor = Exception.class,
            isolation = Isolation.READ_COMMITTED
    )
    public DocumentQualityVO.Detail completeTask(

            Long courseId,

            Long taskId,

            Long userId) {

        lockActiveTask(

                courseId,

                taskId,

                userId
        );

        DocumentQualityVO.Detail detail =
                getDetail(

                        courseId,

                        taskId,

                        userId
                );

        if (!detail.progress().canComplete()) {

            throw conflict(
                    "仍存在未检查样本或未关闭的质量问题"
            );
        }

        // 防止不合格样本没有对应质量问题。
        long inconsistentSamples = count(

                """
                SELECT COUNT(*)
                FROM course_document_quality_sample s
                WHERE s.task_id = ?
                  AND s.status = 'FAIL'
                  AND NOT EXISTS (
                      SELECT 1
                      FROM course_document_quality_issue i
                      WHERE i.task_id = s.task_id
                        AND i.sample_id = s.id
                  )
                """,

                taskId
        );

        if (inconsistentSamples > 0) {

            throw conflict(
                    "存在不合格样本未登记质量问题"
            );
        }

        requireUpdated(

                jdbc.update(

                        """
                        UPDATE course_document_quality_task

                        SET status = 'COMPLETED',
                            completed_by = ?,
                            completed_time = NOW(),
                            update_time = NOW()

                        WHERE id = ?
                          AND course_id = ?
                          AND status = 'IN_PROGRESS'
                        """,

                        userId,

                        taskId,

                        courseId
                ),

                "完成抽检失败，任务状态已经变化"
        );

        logTaskOperation(
                courseId,
                taskId,
                userId,
                "QUALITY_TASK_COMPLETE",
                "完成资料质量抽检任务",
                "完成质量抽检任务ID=" + taskId,
                "IN_PROGRESS",
                "COMPLETED"
        );

        return getDetail(

                courseId,

                taskId,

                userId
        );
    }

    // =================================================
    // 10. 发布审核质量检查
    // =================================================

    /**
     * 获取当前文档的质量发布阻断原因。
     *
     * null 表示不存在质量抽检方面的发布阻碍。
     */
    public String getPublishBlockReason(

            Long courseId,

            Long documentId) {

        long activeTasks = count(
                """
                SELECT COUNT(*)
                FROM course_document_quality_task
                WHERE course_id = ?
                  AND document_id = ?
                  AND status <> 'COMPLETED'
                """,
                courseId,
                documentId
        );

        if (activeTasks > 0) {
            return "当前文档仍存在未完成的质量抽检任务";
        }

        long unresolvedIssues = count(
                """
                SELECT COUNT(*)
                FROM course_document_quality_issue i
                JOIN course_document_quality_task t
                  ON t.id = i.task_id
                WHERE t.course_id = ?
                  AND t.document_id = ?
                  AND i.status <> 'CLOSED'
                """,
                courseId,
                documentId
        );

        if (unresolvedIssues > 0) {
            return "当前文档仍存在未完成整改的质量问题";
        }

        long replacedIssues = count(
                """
                SELECT COUNT(*)
                FROM course_document_quality_issue i
                JOIN course_document_quality_task t
                  ON t.id = i.task_id
                WHERE t.course_id = ?
                  AND t.document_id = ?
                  AND i.status = 'CLOSED'
                  AND i.replacement_document_id IS NOT NULL
                """,
                courseId,
                documentId
        );

        if (replacedIssues > 0) {
            return "当前资料已通过新版本整改，请审核发布整改后的资源版本";
        }

        return null;
    }

    /**
     * 发布事务中的强制校验。
     */
    public void requirePublishable(

            Long courseId,

            Long documentId) {

        String reason = getPublishBlockReason(
                courseId,
                documentId
        );

        if (reason != null) {
            throw conflict(reason);
        }
    }

    /**
     * 查询文档质量控制状态。
     */
    public DocumentQualityVO.Control getDocumentControl(

            Long courseId,

            Long documentId,

            Long userId) {

        requireManage(
                courseId,
                userId
        );

        List<DocumentQualityVO.Control> rows =
                jdbc.query(
                        """
                        SELECT
                            id AS document_id,
                            status,
                            version_status,
                            review_status,
                            quality_status,
                            quality_block_reason,
                            quality_blocked_by,
                            quality_blocked_at
                        FROM course_document
                        WHERE id = ?
                          AND course_id = ?
                        """,
                        (rs, rowNum) -> {

                            String qualityStatus =
                                    rs.getString("quality_status");

                            boolean retrievable =
                                    "READY".equals(
                                            rs.getString("status")
                                    )
                                    && "CURRENT".equals(
                                            rs.getString("version_status")
                                    )
                                    && "APPROVED".equals(
                                            rs.getString("review_status")
                                    )
                                    && "NORMAL".equals(qualityStatus);

                            return new DocumentQualityVO.Control(
                                    stringId(rs, "document_id"),
                                    qualityStatus,
                                    rs.getString("quality_block_reason"),
                                    stringId(rs, "quality_blocked_by"),
                                    dateTime(rs, "quality_blocked_at"),
                                    retrievable
                            );
                        },
                        documentId,
                        courseId
                );

        if (rows.isEmpty()) {
            throw notFound("文档不存在");
        }

        return rows.get(0);
    }

    /**
     * 保存质量控制操作记录。
     */
    private void saveQualityControlLog(

            Long courseId,

            Long documentId,

            Long userId,

            String action,

            String previousStatus,

            String nextStatus,

            String reason) {

        jdbc.update(
                """
                INSERT INTO course_document_quality_control_log (
                    course_id,
                    document_id,
                    operator_id,
                    action,
                    previous_status,
                    next_status,
                    reason
                )
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                courseId,
                documentId,
                userId,
                action,
                previousStatus,
                nextStatus,
                reason
        );
    }

    /**
     * 暂停已发布资料的 RAG 检索。
     */
    @Transactional(
            rollbackFor = Exception.class,
            isolation = Isolation.READ_COMMITTED
    )
    public DocumentQualityVO.Control suspendDocument(

            Long courseId,

            Long documentId,

            Long userId,

            DocumentQualityRequest.ControlAction request) {

        if (request == null) {
            throw badRequest("暂停原因不能为空");
        }

        String reason = text(
                request.reason(),
                1000,
                true,
                "暂停原因"
        );

        CourseDocument document =
                lockQualityDocument(
                        courseId,
                        documentId,
                        userId
                );

        String previousStatus =
                document.getQualityStatus() == null
                        ? "NORMAL"
                        : document.getQualityStatus();

        if ("SUSPENDED".equals(previousStatus)) {
            throw conflict("该资料已经处于暂停检索状态");
        }

        if (!"NORMAL".equals(previousStatus)) {
            throw conflict("当前资料质量状态异常");
        }

        int updated = jdbc.update(
                """
                UPDATE course_document
                SET quality_status = 'SUSPENDED',
                    quality_block_reason = ?,
                    quality_blocked_by = ?,
                    quality_blocked_at = NOW(),
                    update_time = NOW()
                WHERE id = ?
                  AND course_id = ?
                  AND quality_status = 'NORMAL'
                """,
                reason,
                userId,
                documentId,
                courseId
        );

        requireUpdated(
                updated,
                "暂停检索失败，资料状态已经变化"
        );

        saveQualityControlLog(
                courseId,
                documentId,
                userId,
                "SUSPEND",
                previousStatus,
                "SUSPENDED",
                reason
        );

        operationLogService.write(
                courseId,
                documentId,
                userId,
                "DOCUMENT_SUSPEND",
                "暂停资料检索",
                "暂停资料《" + document.getOriginalName()
                        + "》的知识库检索，原因=" + reason,
                "qualityStatus=NORMAL",
                "qualityStatus=SUSPENDED"
        );

        return getDocumentControl(
                courseId,
                documentId,
                userId
        );
    }

    /**
     * 恢复已完成整改的资料检索。
     */
    @Transactional(
            rollbackFor = Exception.class,
            isolation = Isolation.READ_COMMITTED
    )
    public DocumentQualityVO.Control resumeDocument(

            Long courseId,

            Long documentId,

            Long userId,

            DocumentQualityRequest.ControlAction request) {

        if (request == null) {
            throw badRequest("恢复原因不能为空");
        }

        String reason = text(
                request.reason(),
                1000,
                true,
                "恢复原因"
        );

        CourseDocument document =
                lockQualityDocument(
                        courseId,
                        documentId,
                        userId
                );

        if (!"SUSPENDED".equals(
                document.getQualityStatus())) {

            throw conflict("当前资料没有处于暂停检索状态");
        }

        // 必须保证：
        // 1. 不存在进行中的质量抽检任务。
        // 2. 不存在尚未关闭的质量问题。
        // 3. 不存在要求使用新版本整改的历史问题。
        requirePublishable(
                courseId,
                documentId
        );

        int updated = jdbc.update(
                """
                UPDATE course_document
                SET quality_status = 'NORMAL',
                    quality_block_reason = NULL,
                    quality_blocked_by = NULL,
                    quality_blocked_at = NULL,
                    update_time = NOW()
                WHERE id = ?
                  AND course_id = ?
                  AND quality_status = 'SUSPENDED'
                """,
                documentId,
                courseId
        );

        requireUpdated(
                updated,
                "恢复检索失败，资料状态已经变化"
        );

        saveQualityControlLog(
                courseId,
                documentId,
                userId,
                "RESUME",
                "SUSPENDED",
                "NORMAL",
                reason
        );

        operationLogService.write(
                courseId,
                documentId,
                userId,
                "DOCUMENT_RESUME",
                "恢复资料检索",
                "恢复资料《" + document.getOriginalName()
                        + "》的知识库检索，原因=" + reason,
                "qualityStatus=SUSPENDED",
                "qualityStatus=NORMAL"
        );

        return getDocumentControl(
                courseId,
                documentId,
                userId
        );
    }
}
