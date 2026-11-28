package com.edu.ai.hotquestion.service;

import com.edu.ai.audit.service.ContentOperationLogService;
import com.edu.ai.common.BusinessException;
import com.edu.ai.course.service.CoursePermissionService;
import com.edu.ai.hotquestion.dto.CourseHotQuestionCreateRequest;
import com.edu.ai.hotquestion.dto.CourseHotQuestionPublicVO;
import com.edu.ai.hotquestion.dto.CourseHotQuestionReviewRequest;
import com.edu.ai.hotquestion.dto.CourseHotQuestionUpdateRequest;
import com.edu.ai.hotquestion.dto.CourseHotQuestionVO;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class CourseHotQuestionService {

    private final JdbcTemplate jdbc;

    private final CoursePermissionService permissions;

    private final ContentOperationLogService operationLog;

    private static final Set<String> STATUSES = Set.of(
            "DRAFT",
            "PENDING_REVIEW",
            "PUBLISHED",
            "REJECTED",
            "OFFLINE"
    );

    private record SourceStats(
            long questionCount,
            long studentCount
    ) {}

    private record BasicRow(
            Long id,
            Long courseId,
            String status
    ) {}

    public CourseHotQuestionService(
            JdbcTemplate jdbc,
            CoursePermissionService permissions,
            ContentOperationLogService operationLog) {

        this.jdbc = jdbc;
        this.permissions = permissions;
        this.operationLog = operationLog;
    }

    // =====================================================
    // 1. 运营人员和课程负责人查询管理列表
    // =====================================================

    @Transactional(readOnly = true)
    public List<CourseHotQuestionVO> listForManage(
            Long courseId,
            Long userId,
            String status,
            String keyword) {

        permissions.requireManage(courseId, userId);

        if (status != null && !status.isBlank()) {
            status = status.trim().toUpperCase(Locale.ROOT);

            if (!STATUSES.contains(status)) {
                throw new BusinessException(
                        400,
                        "热门问题状态不合法"
                );
            }
        }

        if (keyword != null && keyword.length() > 100) {
            throw new BusinessException(
                    400,
                    "搜索关键词不能超过100个字符"
            );
        }

        StringBuilder sql = new StringBuilder("""
                SELECT
                    h.id,
                    h.course_id,
                    h.knowledge_point_id,
                    kp.title AS knowledge_point_title,
                    h.source_question_text,
                    h.question_text,
                    h.answer_text,
                    h.category,
                    h.question_count,
                    h.student_count,
                    h.status,
                    h.created_by,
                    h.updated_by,
                    h.reviewed_by,
                    h.review_comment,
                    h.created_at,
                    h.submitted_at,
                    h.reviewed_at,
                    h.published_at,
                    h.offlined_at

                FROM course_hot_question h

                LEFT JOIN course_knowledge_point kp
                  ON kp.id = h.knowledge_point_id
                 AND kp.course_id = h.course_id

                WHERE h.course_id = ?
                """);

        List<Object> args = new ArrayList<>();
        args.add(courseId);

        if (status != null && !status.isBlank()) {
            sql.append(" AND h.status = ? ");
            args.add(status);
        }

        if (keyword != null && !keyword.isBlank()) {

            sql.append("""
                    AND (
                        h.question_text LIKE ?
                        OR h.answer_text LIKE ?
                        OR h.source_question_text LIKE ?
                    )
                    """);

            String pattern = "%" + keyword.trim() + "%";

            args.add(pattern);
            args.add(pattern);
            args.add(pattern);
        }

        sql.append("""
                ORDER BY
                    FIELD(
                        h.status,
                        'PENDING_REVIEW',
                        'DRAFT',
                        'REJECTED',
                        'PUBLISHED',
                        'OFFLINE'
                    ),
                    h.sort_order ASC,
                    h.updated_at DESC,
                    h.id DESC

                LIMIT 200
                """);

        return jdbc.query(
                sql.toString(),
                this::mapQuestion,
                args.toArray()
        );
    }

    // =====================================================
    // 2. 学生查询已发布的热门问题
    // =====================================================

    @Transactional(readOnly = true)
    public List<CourseHotQuestionPublicVO> listPublished(
            Long courseId,
            Long userId,
            String keyword,
            String category) {

        permissions.requireActiveRead(
                courseId,
                userId
        );

        if (keyword != null && keyword.length() > 100) {
            throw new BusinessException(
                    400,
                    "搜索关键词不能超过100个字符"
            );
        }

        if (category != null && category.length() > 50) {
            throw new BusinessException(
                    400,
                    "问题分类不能超过50个字符"
            );
        }

        StringBuilder sql = new StringBuilder("""
                SELECT
                    CAST(h.id AS CHAR) AS id,
                    kp.title AS knowledge_point_title,
                    h.question_text,
                    h.answer_text,
                    h.category,
                    h.published_at

                FROM course_hot_question h

                LEFT JOIN course_knowledge_point kp
                  ON kp.id = h.knowledge_point_id
                 AND kp.course_id = h.course_id

                WHERE h.course_id = ?

                  AND h.status = 'PUBLISHED'
                """);

        List<Object> args = new ArrayList<>();
        args.add(courseId);

        if (keyword != null && !keyword.isBlank()) {

            sql.append("""
                    AND (
                        h.question_text LIKE ?
                        OR h.answer_text LIKE ?
                    )
                    """);

            String pattern = "%" + keyword.trim() + "%";

            args.add(pattern);
            args.add(pattern);
        }

        if (category != null && !category.isBlank()) {
            sql.append(" AND h.category = ? ");
            args.add(category.trim());
        }

        sql.append("""
                ORDER BY
                    h.sort_order ASC,
                    h.published_at DESC,
                    h.id DESC

                LIMIT 200
                """);

        return jdbc.query(
                sql.toString(),
                (rs, rowNum) -> new CourseHotQuestionPublicVO(
                        rs.getString("id"),
                        rs.getString("knowledge_point_title"),
                        rs.getString("question_text"),
                        rs.getString("answer_text"),
                        rs.getString("category"),
                        time(rs, "published_at")
                ),
                args.toArray()
        );
    }

    // =====================================================
    // 3. 从高频问题创建标准问答
    // =====================================================

    @Transactional(rollbackFor = Exception.class)
    public CourseHotQuestionVO create(
            Long courseId,
            Long userId,
            CourseHotQuestionCreateRequest request) {

        permissions.requireManage(courseId, userId);

        String sourceQuestion =
                requiredText(
                        request.sourceQuestionText(),
                        2000,
                        "来源问题"
                );

        String question =
                requiredText(
                        request.questionText(),
                        500,
                        "标准问题"
                );

        String answer =
                requiredText(
                        request.answerText(),
                        5000,
                        "标准答案"
                );

        String category =
                normalizeCategory(request.category());

        validateKnowledgePoint(
                courseId,
                request.knowledgePointId()
        );

        String sourceHash = sha256(sourceQuestion);

        // 必须来自真实高频问题。
        SourceStats stats = verifyHotQuestion(
                courseId,
                request.knowledgePointId(),
                sourceQuestion,
                sourceHash
        );

        if (stats.studentCount() < 2
                || stats.questionCount() < 2) {

            throw new BusinessException(
                    409,
                    "该问题尚未达到高频问题整理条件"
            );
        }

        // 防止同一来源问题重复建立多份有效标准问答。
        Long duplicateCount = jdbc.queryForObject(
                """
                SELECT COUNT(*)

                FROM course_hot_question

                WHERE course_id = ?

                  AND source_question_hash = ?

                  AND status <> 'OFFLINE'
                """,
                Long.class,
                courseId,
                sourceHash
        );

        if (duplicateCount != null && duplicateCount > 0) {

            throw new BusinessException(
                    409,
                    "该高频问题已经存在标准问答记录"
            );
        }

        jdbc.update(
                """
                INSERT INTO course_hot_question (

                    course_id,
                    knowledge_point_id,

                    source_question_text,
                    source_question_hash,

                    question_text,
                    answer_text,
                    category,

                    question_count,
                    student_count,

                    status,

                    created_by,
                    updated_by

                )
                VALUES (
                    ?, ?, ?, ?, ?, ?, ?, ?, ?,
                    'DRAFT',
                    ?, ?
                )
                """,

                courseId,
                request.knowledgePointId(),
                sourceQuestion,
                sourceHash,
                question,
                answer,
                category,
                stats.questionCount(),
                stats.studentCount(),
                userId,
                userId
        );

        Long id = jdbc.queryForObject(
                "SELECT LAST_INSERT_ID()",
                Long.class
        );

        if (id == null) {
            throw new BusinessException(
                    500,
                    "标准问答创建失败"
            );
        }

        operationLog.write(
                courseId,
                null,
                userId,
                "HOT_QUESTION_CREATE",
                "创建热门标准问答",
                "根据高频问题整理标准问答：" + question,
                null,
                "status=DRAFT"
        );

        return findById(id);
    }

    // =====================================================
    // 4. 修改草稿、驳回记录或已下架记录
    // =====================================================

    @Transactional(rollbackFor = Exception.class)
    public CourseHotQuestionVO update(
            Long id,
            Long userId,
            CourseHotQuestionUpdateRequest request) {

        BasicRow basic = requireBasic(id);

        permissions.requireManage(
                basic.courseId(),
                userId
        );

        BasicRow row = lockBasic(id);

        if (!Set.of(
                "DRAFT",
                "REJECTED",
                "OFFLINE"
        ).contains(row.status())) {

            throw new BusinessException(
                    409,
                    "当前状态不允许修改，请先下架已发布内容或等待审核结束"
            );
        }

        String question =
                requiredText(
                        request.questionText(),
                        500,
                        "标准问题"
                );

        String answer =
                requiredText(
                        request.answerText(),
                        5000,
                        "标准答案"
                );

        String category =
                normalizeCategory(request.category());

        validateKnowledgePoint(
                row.courseId(),
                request.knowledgePointId()
        );

        jdbc.update(
                """
                UPDATE course_hot_question

                SET knowledge_point_id = ?,

                    question_text = ?,

                    answer_text = ?,

                    category = ?,

                    status = 'DRAFT',

                    updated_by = ?,

                    reviewed_by = NULL,

                    review_comment = NULL,

                    submitted_at = NULL,

                    reviewed_at = NULL,

                    published_at = NULL,

                    updated_at = NOW()

                WHERE id = ?

                  AND status IN (
                      'DRAFT',
                      'REJECTED',
                      'OFFLINE'
                  )
                """,

                request.knowledgePointId(),
                question,
                answer,
                category,
                userId,
                id
        );

        operationLog.write(
                row.courseId(),
                null,
                userId,
                "HOT_QUESTION_UPDATE",
                "修改热门标准问答",
                "修改标准问答：" + question,
                "status=" + row.status(),
                "status=DRAFT"
        );

        return findById(id);
    }

    // =====================================================
    // 5. 提交审核
    // =====================================================

    @Transactional(rollbackFor = Exception.class)
    public CourseHotQuestionVO submit(
            Long id,
            Long userId) {

        BasicRow basic = requireBasic(id);

        permissions.requireManage(
                basic.courseId(),
                userId
        );

        BasicRow row = lockBasic(id);

        if (!"DRAFT".equals(row.status())) {

            throw new BusinessException(
                    409,
                    "只有草稿状态的标准问答才能提交审核"
            );
        }

        int updated = jdbc.update(
                """
                UPDATE course_hot_question

                SET status = 'PENDING_REVIEW',

                    submitted_at = NOW(),

                    updated_by = ?,

                    updated_at = NOW()

                WHERE id = ?

                  AND status = 'DRAFT'
                """,
                userId,
                id
        );

        if (updated != 1) {
            throw new BusinessException(
                    409,
                    "提交审核失败，请刷新后重试"
            );
        }

        operationLog.write(
                row.courseId(),
                null,
                userId,
                "HOT_QUESTION_SUBMIT",
                "提交热门问题审核",
                "标准问答ID：" + id,
                "status=DRAFT",
                "status=PENDING_REVIEW"
        );

        return findById(id);
    }

    // =====================================================
    // 6. 课程负责人审核
    // =====================================================

    @Transactional(rollbackFor = Exception.class)
    public CourseHotQuestionVO review(
            Long id,
            Long userId,
            CourseHotQuestionReviewRequest request) {

        if (request.approved() == null) {
            throw new BusinessException(
                    400,
                    "请明确审核是否通过"
            );
        }

        BasicRow basic = requireBasic(id);

        requireCourseOwner(
                basic.courseId(),
                userId
        );

        BasicRow row = lockBasic(id);

        if (!"PENDING_REVIEW".equals(row.status())) {

            throw new BusinessException(
                    409,
                    "当前标准问答不在待审核状态"
            );
        }

        String comment = optionalText(
                request.comment(),
                1000
        );

        if (!request.approved()
                && (comment == null || comment.isBlank())) {

            throw new BusinessException(
                    400,
                    "审核驳回时必须填写驳回原因"
            );
        }

        String newStatus = request.approved()
                ? "PUBLISHED"
                : "REJECTED";

        int updated = jdbc.update(
                """
                UPDATE course_hot_question

                SET status = ?,

                    reviewed_by = ?,

                    review_comment = ?,

                    reviewed_at = NOW(),

                    published_at = CASE
                        WHEN ? = 'PUBLISHED'
                        THEN NOW()
                        ELSE NULL
                    END,

                    updated_at = NOW()

                WHERE id = ?

                  AND status = 'PENDING_REVIEW'
                """,

                newStatus,
                userId,
                comment,
                newStatus,
                id
        );

        if (updated != 1) {
            throw new BusinessException(
                    409,
                    "审核操作失败，请刷新后重试"
            );
        }

        operationLog.write(
                row.courseId(),
                null,
                userId,
                request.approved()
                        ? "HOT_QUESTION_APPROVE"
                        : "HOT_QUESTION_REJECT",
                request.approved()
                        ? "审核通过热门问题"
                        : "驳回热门问题",
                "标准问答ID：" + id
                        + "，审核意见：" + comment,
                "status=PENDING_REVIEW",
                "status=" + newStatus
        );

        return findById(id);
    }

    // =====================================================
    // 7. 管理员下架
    // =====================================================

    @Transactional(rollbackFor = Exception.class)
    public CourseHotQuestionVO offline(
            Long id,
            Long userId) {

        BasicRow basic = requireBasic(id);

        permissions.requireManage(
                basic.courseId(),
                userId
        );

        BasicRow row = lockBasic(id);

        if (!"PUBLISHED".equals(row.status())) {

            throw new BusinessException(
                    409,
                    "只有已发布的标准问答才能下架"
            );
        }

        jdbc.update(
                """
                UPDATE course_hot_question

                SET status = 'OFFLINE',

                    offlined_at = NOW(),

                    updated_by = ?,

                    updated_at = NOW()

                WHERE id = ?

                  AND status = 'PUBLISHED'
                """,
                userId,
                id
        );

        operationLog.write(
                row.courseId(),
                null,
                userId,
                "HOT_QUESTION_OFFLINE",
                "下架热门标准问答",
                "标准问答ID：" + id,
                "status=PUBLISHED",
                "status=OFFLINE"
        );

        return findById(id);
    }

    // =====================================================
    // 8. 验证来源是否确实属于高频问题
    // =====================================================

    private SourceStats verifyHotQuestion(
            Long courseId,
            Long pointId,
            String sourceQuestion,
            String sourceHash) {

        String pointCondition = pointId == null
                ? " AND qk.point_id IS NULL "
                : " AND qk.point_id = ? ";

        String sql = """
                SELECT

                    COUNT(DISTINCT m.id) AS question_count,

                    COUNT(DISTINCT s.user_id) AS student_count

                FROM chat_message m

                JOIN chat_session s
                  ON s.id = m.session_id

                JOIN sys_user u
                  ON u.id = s.user_id

                LEFT JOIN student_question_knowledge qk
                  ON qk.chat_message_id = m.id
                 AND qk.session_id = m.session_id
                 AND qk.student_id = s.user_id
                 AND qk.course_id = s.course_id

                JOIN course c
                  ON c.id = s.course_id

                WHERE m.role = 'user'

                  AND u.role = 'STUDENT'

                  AND u.status = 1

                  AND c.status = 1

                  AND s.course_id = ?

                  AND SHA2(TRIM(m.content), 256) = ?

                """ + pointCondition + """

                  AND NOT EXISTS (
                      SELECT 1

                      FROM course_exercise_explain_session ees

                      WHERE ees.chat_session_id = s.id
                  )
                """;

        List<Object> args = new ArrayList<>();

        args.add(courseId);
        args.add(sourceHash);

        if (pointId != null) {
            args.add(pointId);
        }

        SourceStats stats = jdbc.queryForObject(
                sql,
                (rs, rowNum) -> new SourceStats(
                        rs.getLong("question_count"),
                        rs.getLong("student_count")
                ),
                args.toArray()
        );

        return stats == null
                ? new SourceStats(0, 0)
                : stats;
    }

    // =====================================================
    // 9. 课程负责人权限
    // =====================================================

    private void requireCourseOwner(
            Long courseId,
            Long userId) {

        Long count = jdbc.queryForObject(
                """
                SELECT COUNT(*)

                FROM course c

                JOIN sys_user u
                  ON u.id = c.user_id

                WHERE c.id = ?

                  AND c.user_id = ?

                  AND c.status = 1

                  AND u.role = 'TEACHER'

                  AND u.status = 1
                """,
                Long.class,
                courseId,
                userId
        );

        if (count == null || count != 1) {

            throw new BusinessException(
                    403,
                    "只有该课程的负责人教师可以审核热门问题"
            );
        }
    }

    // =====================================================
    // 10. 知识点验证
    // =====================================================

    private void validateKnowledgePoint(
            Long courseId,
            Long pointId) {

        if (pointId == null) {
            return;
        }

        Long count = jdbc.queryForObject(
                """
                SELECT COUNT(*)

                FROM course_knowledge_point

                WHERE id = ?

                  AND course_id = ?
                """,
                Long.class,
                pointId,
                courseId
        );

        if (count == null || count != 1) {
            throw new BusinessException(
                    404,
                    "该课程不存在指定知识点"
            );
        }
    }

    // =====================================================
    // 11. 行锁与记录读取
    // =====================================================

    private BasicRow requireBasic(Long id) {

        List<BasicRow> rows = jdbc.query(
                """
                SELECT id, course_id, status

                FROM course_hot_question

                WHERE id = ?
                """,
                (rs, rowNum) -> new BasicRow(
                        rs.getLong("id"),
                        rs.getLong("course_id"),
                        rs.getString("status")
                ),
                id
        );

        if (rows.isEmpty()) {
            throw new BusinessException(
                    404,
                    "标准问答不存在"
            );
        }

        return rows.get(0);
    }

    private BasicRow lockBasic(Long id) {

        List<BasicRow> rows = jdbc.query(
                """
                SELECT id, course_id, status

                FROM course_hot_question

                WHERE id = ?

                FOR UPDATE
                """,
                (rs, rowNum) -> new BasicRow(
                        rs.getLong("id"),
                        rs.getLong("course_id"),
                        rs.getString("status")
                ),
                id
        );

        if (rows.isEmpty()) {
            throw new BusinessException(
                    404,
                    "标准问答不存在"
            );
        }

        return rows.get(0);
    }

    private CourseHotQuestionVO findById(Long id) {

        List<CourseHotQuestionVO> rows = jdbc.query(
                """
                SELECT
                    h.id,
                    h.course_id,
                    h.knowledge_point_id,
                    kp.title AS knowledge_point_title,
                    h.source_question_text,
                    h.question_text,
                    h.answer_text,
                    h.category,
                    h.question_count,
                    h.student_count,
                    h.status,
                    h.created_by,
                    h.updated_by,
                    h.reviewed_by,
                    h.review_comment,
                    h.created_at,
                    h.submitted_at,
                    h.reviewed_at,
                    h.published_at,
                    h.offlined_at

                FROM course_hot_question h

                LEFT JOIN course_knowledge_point kp
                  ON kp.id = h.knowledge_point_id
                 AND kp.course_id = h.course_id

                WHERE h.id = ?
                """,
                this::mapQuestion,
                id
        );

        if (rows.isEmpty()) {
            throw new BusinessException(
                    404,
                    "标准问答不存在"
            );
        }

        return rows.get(0);
    }

    // =====================================================
    // 12. 数据映射
    // =====================================================

    private CourseHotQuestionVO mapQuestion(
            ResultSet rs,
            int rowNum) throws SQLException {

        return new CourseHotQuestionVO(
                stringValue(rs, "id"),
                stringValue(rs, "course_id"),
                stringValue(rs, "knowledge_point_id"),
                rs.getString("knowledge_point_title"),
                rs.getString("source_question_text"),
                rs.getString("question_text"),
                rs.getString("answer_text"),
                rs.getString("category"),
                rs.getLong("question_count"),
                rs.getLong("student_count"),
                rs.getString("status"),
                stringValue(rs, "created_by"),
                stringValue(rs, "updated_by"),
                stringValue(rs, "reviewed_by"),
                rs.getString("review_comment"),
                time(rs, "created_at"),
                time(rs, "submitted_at"),
                time(rs, "reviewed_at"),
                time(rs, "published_at"),
                time(rs, "offlined_at")
        );
    }

    private String stringValue(
            ResultSet rs,
            String column) throws SQLException {

        Object value = rs.getObject(column);

        return value == null
                ? null
                : value.toString();
    }

    private java.time.LocalDateTime time(
            ResultSet rs,
            String column) throws SQLException {

        Timestamp value = rs.getTimestamp(column);

        return value == null
                ? null
                : value.toLocalDateTime();
    }

    // =====================================================
    // 13. 参数校验
    // =====================================================

    private String requiredText(
            String value,
            int max,
            String field) {

        String result = value == null
                ? ""
                : value.trim();

        if (result.isBlank()) {
            throw new BusinessException(
                    400,
                    field + "不能为空"
            );
        }

        if (result.length() > max) {
            throw new BusinessException(
                    400,
                    field + "不能超过" + max + "个字符"
            );
        }

        return result;
    }

    private String optionalText(
            String value,
            int max) {

        if (value == null || value.isBlank()) {
            return null;
        }

        String result = value.trim();

        if (result.length() > max) {
            throw new BusinessException(
                    400,
                    "审核意见不能超过" + max + "个字符"
            );
        }

        return result;
    }

    private String normalizeCategory(String category) {

        if (category == null || category.isBlank()) {
            return "其他";
        }

        return requiredText(
                category,
                50,
                "问题分类"
        );
    }

    private String sha256(String value) {

        try {

            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(
                    value.trim().getBytes(
                            StandardCharsets.UTF_8
                    )
            );

            return HexFormat.of().formatHex(hash);

        } catch (NoSuchAlgorithmException e) {

            throw new IllegalStateException(
                    "SHA-256不可用",
                    e
            );
        }
    }
}
