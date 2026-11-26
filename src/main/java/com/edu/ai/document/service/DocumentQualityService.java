package com.edu.ai.document.service;

import com.edu.ai.common.BusinessException;

import com.edu.ai.course.service.CoursePermissionService;

import com.edu.ai.document.dto.DocumentQualityRequest;
import com.edu.ai.document.dto.DocumentQualityVO;

import com.edu.ai.document.entity.CourseDocument;
import com.edu.ai.document.mapper.CourseDocumentMapper;

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

import java.time.LocalDateTime;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
                    "OTHER"
            );

    private final JdbcTemplate jdbc;

    private final CoursePermissionService permissions;

    private final CourseDocumentMapper documentMapper;

    private final DocumentChunkService chunkService;

    public DocumentQualityService(

            JdbcTemplate jdbc,

            CoursePermissionService permissions,

            CourseDocumentMapper documentMapper,

            DocumentChunkService chunkService) {

        this.jdbc = jdbc;

        this.permissions = permissions;

        this.documentMapper = documentMapper;

        this.chunkService = chunkService;
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
        List<String> documentStatuses =
                jdbc.queryForList(

                        """
                        SELECT status
                        FROM course_document
                        WHERE id = ?
                          AND course_id = ?
                        FOR UPDATE
                        """,

                        String.class,

                        documentId,

                        courseId
                );

        if (documentStatuses.isEmpty()
                || !"READY".equals(
                        documentStatuses.get(0))) {

            throw conflict(
                    "文档尚未完成解析"
            );
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
     * 在知识库发布事务内部调用。
     *
     * 调用方必须已经获得课程行锁。
     */
    public void requirePublishable(

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

            throw conflict(
                    "当前文档仍存在未完成的质量抽检任务"
            );
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

            throw conflict(
                    "当前文档仍存在未完成整改的质量问题"
            );
        }

        // 如果整改依赖新的文档版本，
        // 不允许继续发布存在问题的原版本。
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

            throw conflict(
                    "当前资料的质量问题已通过新版本整改，"
                    + "请审核发布整改后的资源版本"
            );
        }
    }
}
