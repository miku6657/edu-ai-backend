package com.edu.ai.chat.service;

import com.edu.ai.chat.dto.HotQuestionVO;
import com.edu.ai.chat.dto.KnowledgePointQuestionStatVO;
import com.edu.ai.chat.dto.QuestionAnalyticsSummaryVO;
import com.edu.ai.chat.dto.TeacherQuestionRecordVO;
import com.edu.ai.common.BusinessException;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class TeacherQuestionAnalyticsService {

    private final JdbcTemplate jdbc;

    public TeacherQuestionAnalyticsService(
            JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record QuestionPage(
            int page,
            int size,
            long total,
            long totalPages,
            List<TeacherQuestionRecordVO> items
    ) {}

    private static final String QUESTION_FROM = """
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
        LEFT JOIN course_knowledge_point kp
          ON kp.id = qk.point_id
         AND kp.course_id = s.course_id
        LEFT JOIN course_qa_ticket t
          ON t.question_message_id = m.id
         AND t.session_id = s.id
         AND t.student_id = s.user_id
         AND t.course_id = s.course_id
        """;

    private static String time(
            ResultSet rs,
            String column) throws SQLException {

        Timestamp value = rs.getTimestamp(column);

        return value == null
                ? null
                : value.toLocalDateTime().toString();
    }

    /**
     * 验证当前教师是否拥有课程。
     * 管理员允许查看所有课程。
     */
    private void requireCourseAccess(
            Long courseId,
            Long userId) {

        List<String> roles = jdbc.query(
                """
                SELECT role
                FROM sys_user
                WHERE id = ?
                  AND status = 1
                """,
                (rs, rowNum) -> rs.getString("role"),
                userId
        );

        if (roles.isEmpty()) {
            throw new BusinessException(
                    401,
                    "用户不存在或已禁用"
            );
        }

        String role = roles.get(0);

        if ("ADMIN".equals(role)) {

            Integer exists = jdbc.queryForObject(
                    """
                    SELECT COUNT(*)
                    FROM course
                    WHERE id = ?
                    """,
                    Integer.class,
                    courseId
            );

            if (exists == null || exists == 0) {
                throw new BusinessException(
                        404,
                        "课程不存在"
                );
            }

            return;
        }

        if (!"TEACHER".equals(role)) {
            throw new BusinessException(
                    403,
                    "只有教师或管理员可以查看问答分析"
            );
        }

        Integer count = jdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM course
                WHERE id = ?
                  AND user_id = ?
                  AND status = 1
                """,
                Integer.class,
                courseId,
                userId
        );

        if (count == null || count != 1) {
            throw new BusinessException(
                    403,
                    "无权查看该课程的问答记录"
            );
        }
    }

    private void validatePoint(
            Long courseId,
            Long pointId) {

        if (pointId == null) {
            return;
        }

        Integer count = jdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM course_knowledge_point
                WHERE id = ?
                  AND course_id = ?
                """,
                Integer.class,
                pointId,
                courseId
        );

        if (count == null || count != 1) {
            throw new BusinessException(
                    404,
                    "当前课程不存在该知识点"
            );
        }
    }

    private void validateDates(
            LocalDate startDate,
            LocalDate endDate) {

        if (startDate != null
                && endDate != null
                && endDate.isBefore(startDate)) {

            throw new BusinessException(
                    400,
                    "结束日期不能早于开始日期"
            );
        }
    }

    private void validateText(
            String value,
            int maxLength,
            String fieldName) {

        if (value != null
                && value.length() > maxLength) {

            throw new BusinessException(
                    400,
                    fieldName + "长度不能超过"
                            + maxLength + "个字符"
            );
        }
    }

    private void validatePage(
            int page,
            int size) {

        if (page < 1
                || page > 1_000_000
                || size < 1
                || size > 100) {

            throw new BusinessException(
                    400,
                    "分页参数不合法"
            );
        }
    }

    /**
     * 构造提问记录筛选条件。
     */
    private String buildWhere(
            Long courseId,
            Long pointId,
            String studentKeyword,
            String keyword,
            LocalDate startDate,
            LocalDate endDate,
            List<Object> args) {

        StringBuilder where = new StringBuilder(
                """
                WHERE m.role = 'user'
                  AND u.role = 'STUDENT'
                  AND s.course_id = ?
                """
        );

        args.add(courseId);

        if (pointId != null) {
            where.append(" AND qk.point_id = ? ");
            args.add(pointId);
        }

        if (studentKeyword != null
                && !studentKeyword.isBlank()) {

            where.append("""
                AND (
                    u.nickname LIKE ?
                    OR u.username LIKE ?
                )
                """);

            String pattern =
                    "%" + studentKeyword.trim() + "%";

            args.add(pattern);
            args.add(pattern);
        }

        if (keyword != null
                && !keyword.isBlank()) {

            where.append(" AND m.content LIKE ? ");
            args.add("%" + keyword.trim() + "%");
        }

        if (startDate != null) {
            where.append(" AND m.create_time >= ? ");
            args.add(
                    Timestamp.valueOf(
                            startDate.atStartOfDay()
                    )
            );
        }

        if (endDate != null) {
            where.append(" AND m.create_time < ? ");
            args.add(
                    Timestamp.valueOf(
                            endDate.plusDays(1).atStartOfDay()
                    )
            );
        }

        return where.toString();
    }

    /**
     * 教师分页查询学生提问记录。
     */
    public QuestionPage listRecords(
            Long courseId,
            Long userId,
            Long pointId,
            String studentKeyword,
            String keyword,
            LocalDate startDate,
            LocalDate endDate,
            int page,
            int size) {

        requireCourseAccess(courseId, userId);
        validatePoint(courseId, pointId);
        validateDates(startDate, endDate);
        validateText(studentKeyword, 100, "学生筛选条件");
        validateText(keyword, 200, "问题关键词");
        validatePage(page, size);

        List<Object> args = new ArrayList<>();

        String where = buildWhere(
                courseId,
                pointId,
                studentKeyword,
                keyword,
                startDate,
                endDate,
                args
        );

        Long count = jdbc.queryForObject(
                "SELECT COUNT(DISTINCT m.id) "
                        + QUESTION_FROM
                        + where,
                Long.class,
                args.toArray()
        );

        long total = count == null ? 0 : count;

        List<Object> queryArgs =
                new ArrayList<>(args);

        queryArgs.add(size);
        queryArgs.add((long) (page - 1) * size);

        List<TeacherQuestionRecordVO> records =
                jdbc.query(
                        """
                        SELECT DISTINCT
                            m.id AS sort_id,
                            CAST(m.id AS CHAR) AS message_id,
                            CAST(s.id AS CHAR) AS session_id,
                            CAST(s.user_id AS CHAR) AS student_id,

                            COALESCE(
                                NULLIF(u.nickname, ''),
                                u.username
                            ) AS student_name,

                            m.content AS question_text,
                            m.create_time AS asked_at,

                            CAST(qk.point_id AS CHAR)
                                AS knowledge_point_id,

                            kp.title AS knowledge_point_title,

                            CASE
                                WHEN t.status = 'PENDING'
                                    THEN 'HUMAN_PENDING'
                                WHEN t.status = 'ANSWERED'
                                    THEN 'HUMAN_ANSWERED'
                                ELSE 'AI_HANDLED'
                            END AS handling_status,

                            CAST(t.id AS CHAR) AS ticket_id,
                            t.status AS ticket_status,
                            t.no_evidence_reason,
                            t.teacher_reply,

                            (
                                SELECT LEFT(a.content, 1000)
                                FROM chat_message a
                                WHERE a.session_id = m.session_id
                                  AND a.role = 'assistant'
                                  AND a.id > m.id
                                  AND NOT EXISTS (
                                      SELECT 1
                                      FROM chat_message next_user
                                      WHERE next_user.session_id = m.session_id
                                        AND next_user.role = 'user'
                                        AND next_user.id > m.id
                                        AND next_user.id < a.id
                                  )
                                ORDER BY a.id ASC
                                LIMIT 1
                            ) AS ai_answer_preview

                        """
                        + QUESTION_FROM
                        + where
                        + """
                        ORDER BY asked_at DESC, sort_id DESC
                        LIMIT ? OFFSET ?
                        """,
                        (rs, rowNum) -> mapRecord(rs),
                        queryArgs.toArray()
                );

        long totalPages =
                (total + size - 1) / size;

        return new QuestionPage(
                page,
                size,
                total,
                totalPages,
                records
        );
    }

    private TeacherQuestionRecordVO mapRecord(
            ResultSet rs) throws SQLException {

        return new TeacherQuestionRecordVO(
                rs.getString("message_id"),
                rs.getString("session_id"),
                rs.getString("student_id"),
                rs.getString("student_name"),
                rs.getString("question_text"),
                time(rs, "asked_at"),
                rs.getString("knowledge_point_id"),
                rs.getString("knowledge_point_title"),
                rs.getString("handling_status"),
                rs.getString("ticket_id"),
                rs.getString("ticket_status"),
                rs.getString("no_evidence_reason"),
                rs.getString("teacher_reply"),
                rs.getString("ai_answer_preview")
        );
    }

    /**
     * 高频问题统计。
     *
     * 同一知识点、相同问题文本作为一组，
     * 只展示至少被提问两次的问题。
     */
    public List<HotQuestionVO> listHotQuestions(
            Long courseId,
            Long userId,
            Long pointId,
            LocalDate startDate,
            LocalDate endDate,
            int limit) {

        requireCourseAccess(courseId, userId);
        validatePoint(courseId, pointId);
        validateDates(startDate, endDate);

        if (limit < 1 || limit > 50) {
            throw new BusinessException(
                    400,
                    "统计数量必须在1至50之间"
            );
        }

        List<Object> args = new ArrayList<>();

        String where = buildWhere(
                courseId,
                pointId,
                null,
                null,
                startDate,
                endDate,
                args
        );

        args.add(limit);

        return jdbc.query(
                """
                SELECT
                    MIN(m.content) AS question_text,

                    CAST(qk.point_id AS CHAR)
                        AS knowledge_point_id,

                    MAX(kp.title)
                        AS knowledge_point_title,

                    COUNT(DISTINCT m.id)
                        AS question_count,

                    COUNT(DISTINCT s.user_id)
                        AS student_count,

                    MAX(m.create_time)
                        AS last_asked_at

                """
                + QUESTION_FROM
                + where
                + """
                GROUP BY
                    qk.point_id,
                    kp.title,
                    SHA2(TRIM(m.content), 256)

                HAVING COUNT(DISTINCT s.user_id) >= 2

                ORDER BY
                    question_count DESC,
                    last_asked_at DESC

                LIMIT ?
                """,
                (rs, rowNum) -> new HotQuestionVO(
                        rs.getString("question_text"),
                        rs.getString("knowledge_point_id"),
                        rs.getString("knowledge_point_title"),
                        rs.getLong("question_count"),
                        rs.getLong("student_count"),
                        time(rs, "last_asked_at")
                ),
                args.toArray()
        );
    }

    /**
     * 各知识点提问热度。
     */
    public List<KnowledgePointQuestionStatVO>
    listKnowledgePointStats(
            Long courseId,
            Long userId,
            LocalDate startDate,
            LocalDate endDate) {

        requireCourseAccess(courseId, userId);
        validateDates(startDate, endDate);

        StringBuilder sql = new StringBuilder(
                """
                SELECT
                    CAST(p.id AS CHAR) AS point_id,
                    p.title,

                    COUNT(DISTINCT m.id)
                        AS question_count,

                    COUNT(DISTINCT s.user_id)
                        AS student_count,

                    MAX(m.create_time)
                        AS latest_asked_at

                FROM student_question_knowledge qk

                JOIN course_knowledge_point p
                  ON p.id = qk.point_id
                 AND p.course_id = qk.course_id

                JOIN chat_message m
                  ON m.id = qk.chat_message_id
                 AND m.role = 'user'

                JOIN chat_session s
                  ON s.id = m.session_id
                 AND s.course_id = qk.course_id

                WHERE qk.course_id = ?
                """
        );

        List<Object> args = new ArrayList<>();
        args.add(courseId);

        if (startDate != null) {
            sql.append(" AND m.create_time >= ? ");
            args.add(
                    Timestamp.valueOf(
                            startDate.atStartOfDay()
                    )
            );
        }

        if (endDate != null) {
            sql.append(" AND m.create_time < ? ");
            args.add(
                    Timestamp.valueOf(
                            endDate.plusDays(1).atStartOfDay()
                    )
            );
        }

        sql.append("""
                GROUP BY p.id, p.title
                ORDER BY question_count DESC, p.id
                """);

        return jdbc.query(
                sql.toString(),
                (rs, rowNum) ->
                        new KnowledgePointQuestionStatVO(
                                rs.getString("point_id"),
                                rs.getString("title"),
                                rs.getLong("question_count"),
                                rs.getLong("student_count"),
                                time(rs, "latest_asked_at")
                        ),
                args.toArray()
        );
    }

    /**
     * 课程问答整体统计。
     */
    public QuestionAnalyticsSummaryVO summary(
            Long courseId,
            Long userId,
            LocalDate startDate,
            LocalDate endDate) {

        requireCourseAccess(courseId, userId);
        validateDates(startDate, endDate);

        List<Object> args = new ArrayList<>();

        String where = buildWhere(
                courseId,
                null,
                null,
                null,
                startDate,
                endDate,
                args
        );

        List<QuestionAnalyticsSummaryVO> result =
                jdbc.query(
                        """
                        SELECT
                            COUNT(DISTINCT m.id)
                                AS total_questions,

                            COUNT(DISTINCT s.user_id)
                                AS participating_students,

                            COUNT(DISTINCT
                                CASE WHEN qk.chat_message_id IS NOT NULL
                                THEN m.id END
                            ) AS mapped_questions,

                            COUNT(DISTINCT
                                CASE WHEN qk.chat_message_id IS NULL
                                THEN m.id END
                            ) AS unclassified_questions,

                            COUNT(DISTINCT
                                CASE WHEN t.status = 'PENDING'
                                THEN m.id END
                            ) AS pending_tickets,

                            COUNT(DISTINCT
                                CASE WHEN t.status = 'ANSWERED'
                                THEN m.id END
                            ) AS answered_tickets

                        """
                        + QUESTION_FROM
                        + where,
                        (rs, rowNum) ->
                                new QuestionAnalyticsSummaryVO(
                                        rs.getLong("total_questions"),
                                        rs.getLong("participating_students"),
                                        rs.getLong("mapped_questions"),
                                        rs.getLong("unclassified_questions"),
                                        rs.getLong("pending_tickets"),
                                        rs.getLong("answered_tickets")
                                ),
                        args.toArray()
                );

        return result.get(0);
    }
}
