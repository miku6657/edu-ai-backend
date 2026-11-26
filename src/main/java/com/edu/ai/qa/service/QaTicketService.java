package com.edu.ai.qa.service;

import com.edu.ai.common.BusinessException;
import com.edu.ai.notification.service.NotificationService;
import com.edu.ai.qa.dto.QaTicketVO;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

@Service
public class QaTicketService {

    private final JdbcTemplate jdbc;
    private final NotificationService notificationService;

    public QaTicketService(
            JdbcTemplate jdbc,
            NotificationService notificationService) {

        this.jdbc = jdbc;
        this.notificationService = notificationService;
    }

    public record CreatedTicket(
            String ticketId,
            String status
    ) {}

    public record TicketPage(
            int page,
            int size,
            long total,
            long totalPages,
            List<QaTicketVO> items
    ) {}

    private record CourseAssignment(
            Long teacherId,
            String courseName,
            String teacherRole
    ) {}

    private static final RowMapper<QaTicketVO> ROW_MAPPER =
            QaTicketService::mapTicket;

    private static QaTicketVO mapTicket(
            ResultSet rs,
            int rowNum) throws SQLException {

        return new QaTicketVO(
                rs.getString("id"),
                rs.getString("course_id"),
                rs.getString("course_name"),
                rs.getString("student_id"),
                rs.getString("student_name"),
                rs.getString("teacher_id"),
                rs.getString("teacher_name"),
                rs.getString("session_id"),
                rs.getString("question_message_id"),
                rs.getString("question_text"),
                rs.getString("no_evidence_reason"),
                rs.getString("status"),
                rs.getString("teacher_reply"),
                time(rs, "created_at"),
                time(rs, "updated_at"),
                time(rs, "answered_at")
        );
    }

    private static String time(
            ResultSet rs,
            String column) throws SQLException {

        Timestamp value = rs.getTimestamp(column);

        return value == null
                ? null
                : value.toLocalDateTime().toString();
    }

    /**
     * 无依据问答自动生成工单。
     *
     * question_message_id 唯一索引负责幂等。
     */
    @Transactional(rollbackFor = Exception.class)
    public CreatedTicket createFromNoEvidence(
            Long courseId,
            Long studentId,
            Long sessionId,
            Long questionMessageId,
            String question,
            String reason) {

        if (courseId == null
                || studentId == null
                || sessionId == null
                || questionMessageId == null
                || question == null
                || question.isBlank()) {

            throw new BusinessException(
                    400,
                    "创建答疑工单所需参数不完整"
            );
        }

        if (question.length() > 2000) {
            throw new BusinessException(
                    400,
                    "问题内容超过限制"
            );
        }

        String normalizedReason =
                normalizeReason(reason);

        // 1. 校验学生、课程成员关系
        Long studentCount = jdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM sys_user u
                JOIN course_student cs
                  ON cs.student_id = u.id
                JOIN course c
                  ON c.id = cs.course_id
                WHERE u.id = ?
                  AND u.role = 'STUDENT'
                  AND u.status = 1
                  AND c.id = ?
                  AND c.status = 1
                """,
                Long.class,
                studentId,
                courseId
        );

        if (studentCount == null || studentCount != 1) {
            throw new BusinessException(
                    403,
                    "学生不属于当前课程或课程已停用"
            );
        }

        // 2. 查询课程负责人，作为任课教师
        List<CourseAssignment> assignments =
                jdbc.query(
                        """
                        SELECT
                            c.user_id AS teacher_id,
                            c.name AS course_name,
                            u.role AS teacher_role
                        FROM course c
                        JOIN sys_user u
                          ON u.id = c.user_id
                        WHERE c.id = ?
                          AND c.status = 1
                        """,
                        (rs, rowNum) -> new CourseAssignment(
                                rs.getLong("teacher_id"),
                                rs.getString("course_name"),
                                rs.getString("teacher_role")
                        ),
                        courseId
                );

        if (assignments.isEmpty()) {
            throw new BusinessException(
                    404,
                    "没有找到课程负责人"
            );
        }

        CourseAssignment assignment =
                assignments.get(0);

        if (!"TEACHER".equals(assignment.teacherRole())) {
            throw new BusinessException(
                    409,
                    "课程负责人不是有效的教师账号"
            );
        }

        Long teacherId = assignment.teacherId();

        // 3. 幂等插入工单
        jdbc.update(
                """
                INSERT IGNORE INTO course_qa_ticket (
                    course_id,
                    student_id,
                    teacher_id,
                    session_id,
                    question_message_id,
                    question_text,
                    no_evidence_reason,
                    status,
                    created_at,
                    updated_at
                )
                VALUES (
                    ?, ?, ?, ?, ?, ?, ?,
                    'PENDING',
                    NOW(3),
                    NOW(3)
                )
                """,
                courseId,
                studentId,
                teacherId,
                sessionId,
                questionMessageId,
                question.trim(),
                normalizedReason
        );

        // 4. 无论本次是新建还是重复请求，都取回同一工单
        List<Long> ticketIds = jdbc.query(
                """
                SELECT id
                FROM course_qa_ticket
                WHERE question_message_id = ?
                """,
                (rs, rowNum) -> rs.getLong("id"),
                questionMessageId
        );

        if (ticketIds.isEmpty()) {
            throw new IllegalStateException(
                    "答疑工单创建失败"
            );
        }

        Long ticketId = ticketIds.get(0);

        // 5. 给任课教师发送站内通知
        try {
            notificationService.createIfAbsent(
                    teacherId,
                    "QA_TICKET_CREATED",
                    ticketId,
                    "收到新的课程答疑",
                    "课程《" + assignment.courseName()
                            + "》有学生提出的问题需要人工答疑。"
            );
        } catch (Exception e) {
            // 通知失败不能回滚已经创建的工单
            // 具体告警交由统一日志监控处理
        }

        return new CreatedTicket(
                ticketId.toString(),
                "PENDING"
        );
    }

    private String normalizeReason(String reason) {

        if ("NO_ACTIVE_DOCUMENTS".equals(reason)) {
            return reason;
        }

        if ("NO_RETRIEVAL_MATCH".equals(reason)) {
            return reason;
        }

        return "NO_RETRIEVAL_MATCH";
    }

    /**
     * 学生查看自己的工单。
     */
    public TicketPage listMine(
            Long userId,
            String status,
            int page,
            int size) {

        requireRole(userId, "STUDENT");
        validatePage(page, size);
        validateStatus(status);

        String where = " WHERE t.student_id = ?";

        List<Object> args = new ArrayList<>();
        args.add(userId);

        if (status != null) {
            where += " AND t.status = ?";
            args.add(status);
        }

        return queryPage(where, args, page, size);
    }

    /**
     * 教师查看分配给自己的工单。
     * 管理员可以查看全部工单。
     */
    public TicketPage listAssigned(
            Long userId,
            Long courseId,
            String status,
            int page,
            int size) {

        String role = requireActiveRole(userId);

        if (!"TEACHER".equals(role)
                && !"ADMIN".equals(role)) {

            throw new BusinessException(
                    403,
                    "只有教师或管理员可以查看教师工单"
            );
        }

        validatePage(page, size);
        validateStatus(status);

        StringBuilder where = new StringBuilder(
                " WHERE 1 = 1"
        );

        List<Object> args = new ArrayList<>();

        if ("TEACHER".equals(role)) {
            where.append(" AND t.teacher_id = ?");
            args.add(userId);
        }

        if (courseId != null) {
            where.append(" AND t.course_id = ?");
            args.add(courseId);
        }

        if (status != null) {
            where.append(" AND t.status = ?");
            args.add(status);
        }

        return queryPage(
                where.toString(),
                args,
                page,
                size
        );
    }

    /**
     * 查看工单详情。
     */
    public QaTicketVO detail(
            Long ticketId,
            Long userId) {

        QaTicketVO ticket = findTicket(ticketId);

        if (ticket == null) {
            throw new BusinessException(
                    404,
                    "答疑工单不存在"
            );
        }

        String role = requireActiveRole(userId);

        boolean allowed =
                ("STUDENT".equals(role)
                        && userId.toString().equals(
                                ticket.studentId()
                        ))
                || ("TEACHER".equals(role)
                        && userId.toString().equals(
                                ticket.teacherId()
                        ))
                || "ADMIN".equals(role);

        if (!allowed) {
            throw new BusinessException(
                    403,
                    "无权查看该答疑工单"
            );
        }

        return ticket;
    }

    /**
     * 教师提交答复。
     */
    @Transactional(rollbackFor = Exception.class)
    public void reply(
            Long ticketId,
            Long teacherId,
            String reply) {

        if (reply == null || reply.isBlank()) {
            throw new BusinessException(
                    400,
                    "答复内容不能为空"
            );
        }

        String cleaned = reply.trim();

        if (cleaned.length() > 5000) {
            throw new BusinessException(
                    400,
                    "答复内容不能超过5000个字符"
            );
        }

        QaTicketVO ticket = findTicket(ticketId);

        if (ticket == null) {
            throw new BusinessException(
                    404,
                    "答疑工单不存在"
            );
        }

        String role = requireActiveRole(teacherId);

        boolean isAdmin = "ADMIN".equals(role);

        if (!isAdmin
                && !("TEACHER".equals(role)
                && teacherId.toString().equals(
                        ticket.teacherId()
                ))) {

            throw new BusinessException(
                    403,
                    "无权答复该工单"
            );
        }

        if (!"PENDING".equals(ticket.status())) {
            throw new BusinessException(
                    409,
                    "该工单已经处理，不能重复答复"
            );
        }

        String sql = isAdmin
                ? """
                  UPDATE course_qa_ticket
                  SET teacher_reply = ?,
                      status = 'ANSWERED',
                      answered_at = NOW(3),
                      updated_at = NOW(3)
                  WHERE id = ?
                    AND status = 'PENDING'
                  """
                : """
                  UPDATE course_qa_ticket
                  SET teacher_reply = ?,
                      status = 'ANSWERED',
                      answered_at = NOW(3),
                      updated_at = NOW(3)
                  WHERE id = ?
                    AND teacher_id = ?
                    AND status = 'PENDING'
                  """;

        int updated = isAdmin
                ? jdbc.update(sql, cleaned, ticketId)
                : jdbc.update(
                        sql,
                        cleaned,
                        ticketId,
                        teacherId
                );

        if (updated != 1) {
            throw new BusinessException(
                    409,
                    "工单状态已经发生变化，请刷新后重试"
            );
        }

        // 通知学生教师已经答复
        try {
            notificationService.createIfAbsent(
                    Long.parseLong(ticket.studentId()),
                    "QA_TICKET_ANSWERED",
                    ticketId,
                    "你的课程问题已有答复",
                    "课程《" + ticket.courseName()
                            + "》中的问题已经由教师答复。"
            );
        } catch (Exception e) {
            // 不影响已经保存的教师答复
        }
    }

    private TicketPage queryPage(
            String where,
            List<Object> args,
            int page,
            int size) {

        Long count = jdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM course_qa_ticket t
                """
                + where,
                Long.class,
                args.toArray()
        );

        long total = count == null ? 0 : count;

        long offset = (long) (page - 1) * size;

        List<Object> queryArgs = new ArrayList<>(args);
        queryArgs.add(size);
        queryArgs.add(offset);

        List<QaTicketVO> items = jdbc.query(
                """
                SELECT
                    CAST(t.id AS CHAR) AS id,
                    CAST(t.course_id AS CHAR) AS course_id,
                    c.name AS course_name,
                    CAST(t.student_id AS CHAR) AS student_id,
                    s.nickname AS student_name,
                    CAST(t.teacher_id AS CHAR) AS teacher_id,
                    teacher.nickname AS teacher_name,
                    CAST(t.session_id AS CHAR) AS session_id,
                    CAST(t.question_message_id AS CHAR)
                        AS question_message_id,
                    t.question_text,
                    t.no_evidence_reason,
                    t.status,
                    t.teacher_reply,
                    t.created_at,
                    t.updated_at,
                    t.answered_at
                FROM course_qa_ticket t
                JOIN course c
                  ON c.id = t.course_id
                JOIN sys_user s
                  ON s.id = t.student_id
                JOIN sys_user teacher
                  ON teacher.id = t.teacher_id
                """
                + where
                + """
                ORDER BY
                    CASE WHEN t.status = 'PENDING'
                         THEN 0 ELSE 1 END,
                    t.created_at DESC,
                    t.id DESC
                LIMIT ? OFFSET ?
                """,
                ROW_MAPPER,
                queryArgs.toArray()
        );

        long pages = (total + size - 1) / size;

        return new TicketPage(
                page,
                size,
                total,
                pages,
                items
        );
    }

    private QaTicketVO findTicket(Long ticketId) {

        if (ticketId == null || ticketId <= 0) {
            throw new BusinessException(
                    400,
                    "工单ID不合法"
            );
        }

        List<QaTicketVO> result = jdbc.query(
                """
                SELECT
                    CAST(t.id AS CHAR) AS id,
                    CAST(t.course_id AS CHAR) AS course_id,
                    c.name AS course_name,
                    CAST(t.student_id AS CHAR) AS student_id,
                    s.nickname AS student_name,
                    CAST(t.teacher_id AS CHAR) AS teacher_id,
                    teacher.nickname AS teacher_name,
                    CAST(t.session_id AS CHAR) AS session_id,
                    CAST(t.question_message_id AS CHAR)
                        AS question_message_id,
                    t.question_text,
                    t.no_evidence_reason,
                    t.status,
                    t.teacher_reply,
                    t.created_at,
                    t.updated_at,
                    t.answered_at
                FROM course_qa_ticket t
                JOIN course c
                  ON c.id = t.course_id
                JOIN sys_user s
                  ON s.id = t.student_id
                JOIN sys_user teacher
                  ON teacher.id = t.teacher_id
                WHERE t.id = ?
                """,
                ROW_MAPPER,
                ticketId
        );

        return result.isEmpty()
                ? null
                : result.get(0);
    }

    private String requireActiveRole(Long userId) {

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

        return roles.get(0);
    }

    private void requireRole(
            Long userId,
            String expectedRole) {

        if (!expectedRole.equals(requireActiveRole(userId))) {
            throw new BusinessException(
                    403,
                    "当前角色无权执行此操作"
            );
        }
    }

    private void validatePage(int page, int size) {

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

    private void validateStatus(String status) {

        if (status != null
                && !status.equals("PENDING")
                && !status.equals("ANSWERED")) {

            throw new BusinessException(
                    400,
                    "工单状态不合法"
            );
        }
    }
}
