package com.edu.ai.exercise.service;

import com.edu.ai.common.BusinessException;
import com.edu.ai.course.service.CoursePermissionService;
import com.edu.ai.exercise.dto.TeacherExerciseExplainAnalyticsVO;
import com.edu.ai.exercise.dto.TeacherExerciseExplainAnalyticsVO.KnowledgePointStat;
import com.edu.ai.exercise.dto.TeacherExerciseExplainAnalyticsVO.HotQuestionStat;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class TeacherExerciseExplainAnalyticsService {

    private final JdbcTemplate jdbc;
    private final CoursePermissionService permissions;

    public TeacherExerciseExplainAnalyticsService(
            JdbcTemplate jdbc,
            CoursePermissionService permissions) {

        this.jdbc = jdbc;
        this.permissions = permissions;
    }

    /**
     * 查询课程 AI 错题讲解统计。
     */
    public TeacherExerciseExplainAnalyticsVO query(
            Long courseId,
            Long userId,
            LocalDate startDate,
            LocalDate endDate) {

        if (courseId == null || courseId <= 0) {
            throw new BusinessException(400, "课程ID无效");
        }

        permissions.requireManage(courseId, userId);
        validateDates(startDate, endDate);

        // 1. 整体统计
        List<Object> summaryArgs = new ArrayList<>();
        String summaryWhere = buildWhere(
                courseId,
                startDate,
                endDate,
                summaryArgs
        );

        SummaryRow summary = jdbc.queryForObject(
                """
                SELECT
                    COUNT(DISTINCT x.chat_session_id)
                        AS session_count,

                    COUNT(DISTINCT x.student_id)
                        AS student_count,

                    COUNT(DISTINCT x.question_id)
                        AS question_count,

                    COUNT(m.id)
                        AS message_count,

                    MAX(m.create_time)
                        AS latest_activity_at

                FROM course_exercise_explain_session x

                JOIN chat_message m
                  ON m.session_id = x.chat_session_id
                 AND m.role = 'user'

                JOIN course_exercise_question q
                  ON q.id = x.question_id

                JOIN course_exercise e
                  ON e.id = q.exercise_id
                 AND e.course_id = x.course_id

                """
                + summaryWhere,
                (rs, rowNum) -> new SummaryRow(
                        rs.getLong("session_count"),
                        rs.getLong("student_count"),
                        rs.getLong("question_count"),
                        rs.getLong("message_count"),
                        toLocalDateTime(
                                rs.getTimestamp("latest_activity_at")
                        )
                ),
                summaryArgs.toArray()
        );

        if (summary == null) {
            summary = new SummaryRow(
                    0L, 0L, 0L, 0L, null
            );
        }

        long followUpCount = Math.max(
                0L,
                summary.messageCount() - summary.sessionCount()
        );

        // 2. 按知识点统计
        List<Object> pointArgs = new ArrayList<>();

        String pointWhere = buildWhere(
                courseId,
                startDate,
                endDate,
                pointArgs
        );

        List<KnowledgePointStat> points = jdbc.query(
                """
                SELECT
                    CAST(kp.id AS CHAR) AS point_id,

                    COALESCE(
                        kp.title,
                        '未关联知识点'
                    ) AS point_title,

                    COUNT(DISTINCT x.chat_session_id)
                        AS session_count,

                    COUNT(DISTINCT x.student_id)
                        AS student_count,

                    COUNT(m.id)
                        AS request_count,

                    MAX(m.create_time)
                        AS latest_activity_at

                FROM course_exercise_explain_session x

                JOIN chat_message m
                  ON m.session_id = x.chat_session_id
                 AND m.role = 'user'

                JOIN course_exercise_question q
                  ON q.id = x.question_id

                JOIN course_exercise e
                  ON e.id = q.exercise_id
                 AND e.course_id = x.course_id

                LEFT JOIN course_knowledge_point kp
                  ON kp.id = q.knowledge_point_id
                 AND kp.course_id = x.course_id

                """
                + pointWhere
                + """
                GROUP BY kp.id, kp.title

                ORDER BY
                    session_count DESC,
                    request_count DESC,
                    latest_activity_at DESC

                """,
                (rs, rowNum) -> new KnowledgePointStat(
                        rs.getString("point_id"),
                        rs.getString("point_title"),
                        rs.getLong("session_count"),
                        rs.getLong("student_count"),
                        rs.getLong("request_count"),
                        toLocalDateTime(
                                rs.getTimestamp("latest_activity_at")
                        )
                ),
                pointArgs.toArray()
        );

        // 3. 高频讲解题目
        List<Object> questionArgs = new ArrayList<>();

        String questionWhere = buildWhere(
                courseId,
                startDate,
                endDate,
                questionArgs
        );

        questionArgs.add(10);

        List<HotQuestionStat> hotQuestions = jdbc.query(
                """
                SELECT
                    CAST(q.id AS CHAR) AS question_id,

                    LEFT(q.stem, 500) AS stem,

                    COALESCE(
                        kp.title,
                        '未关联知识点'
                    ) AS knowledge_point_title,

                    COUNT(DISTINCT x.chat_session_id)
                        AS session_count,

                    COUNT(DISTINCT x.student_id)
                        AS student_count,

                    COUNT(m.id)
                        AS request_count,

                    MAX(m.create_time)
                        AS latest_activity_at

                FROM course_exercise_explain_session x

                JOIN chat_message m
                  ON m.session_id = x.chat_session_id
                 AND m.role = 'user'

                JOIN course_exercise_question q
                  ON q.id = x.question_id

                JOIN course_exercise e
                  ON e.id = q.exercise_id
                 AND e.course_id = x.course_id

                LEFT JOIN course_knowledge_point kp
                  ON kp.id = q.knowledge_point_id
                 AND kp.course_id = x.course_id

                """
                + questionWhere
                + """
                GROUP BY
                    q.id,
                    q.stem,
                    kp.title

                ORDER BY
                    session_count DESC,
                    request_count DESC,
                    latest_activity_at DESC

                LIMIT ?
                """,
                (rs, rowNum) -> new HotQuestionStat(
                        rs.getString("question_id"),
                        rs.getString("stem"),
                        rs.getString("knowledge_point_title"),
                        rs.getLong("session_count"),
                        rs.getLong("student_count"),
                        rs.getLong("request_count"),
                        toLocalDateTime(
                                rs.getTimestamp("latest_activity_at")
                        )
                ),
                questionArgs.toArray()
        );

        return new TeacherExerciseExplainAnalyticsVO(
                summary.sessionCount(),
                summary.studentCount(),
                summary.questionCount(),
                summary.messageCount(),
                followUpCount,
                summary.latestActivityAt(),
                points,
                hotQuestions
        );
    }

    /**
     * 公共统计条件。
     */
    private String buildWhere(
            Long courseId,
            LocalDate startDate,
            LocalDate endDate,
            List<Object> args) {

        StringBuilder sql = new StringBuilder(
                """
                WHERE x.course_id = ?
                  AND m.role = 'user'
                """
        );

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

        return sql.toString();
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

    private LocalDateTime toLocalDateTime(
            Timestamp timestamp) {

        return timestamp == null
                ? null
                : timestamp.toLocalDateTime();
    }

    private record SummaryRow(
            Long sessionCount,
            Long studentCount,
            Long questionCount,
            Long messageCount,
            LocalDateTime latestActivityAt
    ) {}
}
