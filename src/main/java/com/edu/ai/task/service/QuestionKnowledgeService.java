package com.edu.ai.task.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

@Service
public class QuestionKnowledgeService {

    private final JdbcTemplate jdbc;

    public QuestionKnowledgeService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private record Point(
            Long id,
            String title,
            String keywords
    ) {}

    public record WeakPoint(
            String pointId,
            String title,
            int effectiveCount,
            int sessionCount,
            int inferredLevel
    ) {}

    /**
     * 问答成功后记录一次知识点关联。
     * 只处理仍属于该课程的学生本人消息。
     */
    public void recordSuccessfulQuestion(
            Long messageId,
            Long sessionId,
            Long studentId,
            Long courseId,
            String question) {

        if (messageId == null || sessionId == null
                || studentId == null || courseId == null
                || question == null || question.isBlank()) {
            return;
        }

        Integer valid = jdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM chat_message m
                JOIN chat_session s
                  ON s.id = m.session_id
                JOIN sys_user u
                  ON u.id = s.user_id
                JOIN course_student cs
                  ON cs.course_id = s.course_id
                 AND cs.student_id = s.user_id
                JOIN course c
                  ON c.id = s.course_id
                WHERE m.id = ?
                  AND m.session_id = ?
                  AND m.role = 'user'
                  AND s.id = ?
                  AND s.user_id = ?
                  AND s.course_id = ?
                  AND u.role = 'STUDENT'
                  AND u.status = 1
                  AND c.status = 1
                """,
                Integer.class,
                messageId,
                sessionId,
                sessionId,
                studentId,
                courseId
        );

        if (valid == null || valid == 0) {
            return;
        }

        List<Point> points = jdbc.query(
                """
                SELECT id, title, match_keywords
                FROM course_knowledge_point
                WHERE course_id = ?
                ORDER BY id
                """,
                (rs, rowNum) -> new Point(
                        rs.getLong("id"),
                        rs.getString("title"),
                        rs.getString("match_keywords")
                ),
                courseId
        );

        Point bestPoint = null;
        int bestScore = 0;

        for (Point point : points) {
            int score = matchScore(question, point);

            if (score > bestScore) {
                bestScore = score;
                bestPoint = point;
            }
        }

        // 没有可靠关键词命中时，不强行归类。
        if (bestPoint == null) {
            return;
        }

        jdbc.update(
                """
                INSERT IGNORE INTO student_question_knowledge
                    (chat_message_id, session_id,
                     student_id, course_id, point_id)
                VALUES (?, ?, ?, ?, ?)
                """,
                messageId,
                sessionId,
                studentId,
                courseId,
                bestPoint.id()
        );
    }

    /**
     * 匹配规则：
     * 中文至少两个字符；
     * 英文字母/数字关键词按完整单词匹配；
     * 最长命中的关键词优先。
     */
    private int matchScore(String question, Point point) {
        int score = keywordScore(question, point.title());

        if (point.keywords() == null) {
            return score;
        }

        for (String keyword :
                point.keywords().split("[,，;；\\n]")) {
            score = Math.max(
                    score,
                    keywordScore(question, keyword)
            );
        }

        return score;
    }

    private int keywordScore(String question, String raw) {
        if (raw == null) {
            return 0;
        }

        String keyword = raw.trim();
        if (keyword.length() < 2 || keyword.length() > 80) {
            return 0;
        }

        // 过于宽泛的英文关键词不用于自动识别。
        String lower = keyword.toLowerCase(Locale.ROOT);

        if (Set.of("map", "set", "list", "java", "class")
                .contains(lower)) {
            return 0;
        }

        boolean matched;

        if (keyword.matches("[A-Za-z0-9_]+")) {
            Pattern pattern = Pattern.compile(
                    "(?<![A-Za-z0-9_])"
                    + Pattern.quote(keyword)
                    + "(?![A-Za-z0-9_])",
                    Pattern.CASE_INSENSITIVE
            );
            matched = pattern.matcher(question).find();
        } else {
            matched = question.toLowerCase(Locale.ROOT)
                    .contains(lower);
        }

        return matched ? keyword.length() : 0;
    }

    /**
     * 最近30天的有效提问统计。
     * 同一会话、同一知识点最多计2次。
     */
    public List<WeakPoint> listWeakPoints(
            Long courseId,
            Long studentId) {

        return jdbc.query(
                """
                SELECT
                    p.id AS point_id,
                    p.title,
                    COALESCE(q.effective_count, 0)
                        AS effective_count,
                    COALESCE(q.session_count, 0)
                        AS session_count
                FROM course_knowledge_point p
                LEFT JOIN (
                    SELECT
                        point_id,
                        SUM(LEAST(question_count, 2))
                            AS effective_count,
                        COUNT(*) AS session_count
                    FROM (
                        SELECT
                            point_id,
                            session_id,
                            COUNT(*) AS question_count
                        FROM student_question_knowledge
                        WHERE course_id = ?
                          AND student_id = ?
                          AND asked_at >= NOW()
                              - INTERVAL 30 DAY
                        GROUP BY point_id, session_id
                    ) session_stats
                    GROUP BY point_id
                ) q ON q.point_id = p.id
                WHERE p.course_id = ?
                ORDER BY effective_count DESC, p.id
                """,
                (rs, rowNum) -> {
                    int count =
                            rs.getInt("effective_count");
                    int sessions =
                            rs.getInt("session_count");

                    int level = 2;

                    if (count >= 6 && sessions >= 3) {
                        level = 0;
                    } else if (count >= 3 && sessions >= 2) {
                        level = 1;
                    }

                    return new WeakPoint(
                            String.valueOf(
                                    rs.getLong("point_id")),
                            rs.getString("title"),
                            count,
                            sessions,
                            level
                    );
                },
                courseId,
                studentId,
                courseId
        );
    }

    public Map<Long, Integer> inferredMastery(
            Long courseId,
            Long studentId) {

        Map<Long, Integer> result = new HashMap<>();

        for (WeakPoint point :
                listWeakPoints(courseId, studentId)) {

            result.put(
                    Long.valueOf(point.pointId()),
                    point.inferredLevel()
            );
        }

        return result;
    }

    /**
     * 用于删除聊天会话时清理关联数据。
     */
    public void deleteSessionRecords(Long sessionId) {
        jdbc.update(
                """
                DELETE FROM student_question_knowledge
                WHERE session_id = ?
                """,
                sessionId
        );
    }
}
