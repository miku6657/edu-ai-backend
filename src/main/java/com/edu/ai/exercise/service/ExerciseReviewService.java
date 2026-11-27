package com.edu.ai.exercise.service;

import com.edu.ai.common.BusinessException;
import com.edu.ai.course.service.CoursePermissionService;
import com.edu.ai.exercise.dto.ExerciseGradeRequest;
import com.edu.ai.exercise.dto.ExerciseReviewDetailVO;
import com.edu.ai.exercise.dto.ExerciseReviewListVO;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class ExerciseReviewService {

    private final JdbcTemplate jdbc;
    private final CoursePermissionService permissions;

    public ExerciseReviewService(
            JdbcTemplate jdbc,
            CoursePermissionService permissions) {
        this.jdbc = jdbc;
        this.permissions = permissions;
    }

    /**
     * 查询待批改练习。
     *
     * 教师只能查看自己课程的答题记录。
     * 管理员可以查看全部课程。
     */
    public List<ExerciseReviewListVO> listPending(
            Long userId,
            String role,
            Long courseId) {

        if (courseId != null) {
            permissions.requireManage(courseId, userId);
        }

        StringBuilder sql = new StringBuilder("""
                SELECT
                    a.id AS attempt_id,
                    e.id AS exercise_id,
                    e.title AS exercise_title,
                    c.id AS course_id,
                    c.name AS course_name,
                    a.student_id,
                    COALESCE(NULLIF(u.nickname, ''), u.username)
                        AS student_name,
                    a.attempt_no,
                    COUNT(aa.id) AS pending_review_count,
                    a.total_score,
                    a.earned_score,
                    a.submitted_at

                FROM course_exercise_attempt a

                JOIN course_exercise e
                    ON e.id = a.exercise_id

                JOIN course c
                    ON c.id = a.course_id

                JOIN sys_user u
                    ON u.id = a.student_id

                JOIN course_exercise_attempt_answer aa
                    ON aa.attempt_id = a.id
                   AND aa.pending_review = 1

                WHERE a.status = 'PENDING_REVIEW'
                  AND (? = 'ADMIN' OR c.user_id = ?)
                """);

        List<Object> args = new ArrayList<>();
        args.add(role);
        args.add(userId);

        if (courseId != null) {
            sql.append(" AND a.course_id = ? ");
            args.add(courseId);
        }

        sql.append("""
                GROUP BY
                    a.id,
                    e.id,
                    e.title,
                    c.id,
                    c.name,
                    a.student_id,
                    u.nickname,
                    u.username,
                    a.attempt_no,
                    a.total_score,
                    a.earned_score,
                    a.submitted_at

                ORDER BY a.submitted_at ASC, a.id ASC
                """);

        return jdbc.query(
                sql.toString(),
                (rs, rowNum) -> new ExerciseReviewListVO(
                        rs.getString("attempt_id"),
                        rs.getString("exercise_id"),
                        rs.getString("exercise_title"),
                        rs.getString("course_id"),
                        rs.getString("course_name"),
                        rs.getString("student_id"),
                        rs.getString("student_name"),
                        rs.getInt("attempt_no"),
                        rs.getInt("pending_review_count"),
                        rs.getBigDecimal("total_score"),
                        rs.getBigDecimal("earned_score"),
                        toLocalDateTime(rs.getTimestamp("submitted_at"))
                ),
                args.toArray()
        );
    }

    /**
     * 查看一次答题记录的详细内容。
     */
    public ExerciseReviewDetailVO detail(
            Long attemptId,
            Long userId) {

        AttemptHeader attempt = requireAttempt(attemptId);

        permissions.requireManage(
                attempt.courseId(),
                userId
        );

        List<ExerciseReviewDetailVO.Answer> answers =
                jdbc.query(
                        """
                        SELECT
                            aa.id AS answer_id,
                            q.id AS question_id,
                            kp.title AS knowledge_point_title,
                            q.question_type,
                            q.stem,
                            aa.student_answer,
                            q.correct_answer,
                            q.explanation,
                            q.score AS max_score,
                            aa.score_earned,
                            aa.teacher_feedback,
                            aa.pending_review

                        FROM course_exercise_attempt_answer aa

                        JOIN course_exercise_question q
                            ON q.id = aa.question_id

                        LEFT JOIN course_knowledge_point kp
                            ON kp.id = q.knowledge_point_id
                           AND kp.course_id = ?

                        WHERE aa.attempt_id = ?

                        ORDER BY q.sort_order, q.id
                        """,
                        (rs, rowNum) ->
                                new ExerciseReviewDetailVO.Answer(
                                        rs.getString("answer_id"),
                                        rs.getString("question_id"),
                                        rs.getString("knowledge_point_title"),
                                        rs.getString("question_type"),
                                        rs.getString("stem"),
                                        rs.getString("student_answer"),
                                        rs.getString("correct_answer"),
                                        rs.getString("explanation"),
                                        rs.getBigDecimal("max_score"),
                                        rs.getBigDecimal("score_earned"),
                                        rs.getString("teacher_feedback"),
                                        rs.getInt("pending_review") == 1
                                ),
                        attempt.courseId(),
                        attemptId
                );

        return new ExerciseReviewDetailVO(
                attempt.id().toString(),
                attempt.exerciseId().toString(),
                attempt.exerciseTitle(),
                attempt.studentId().toString(),
                attempt.studentName(),
                attempt.attemptNo(),
                attempt.status(),
                attempt.totalScore(),
                attempt.earnedScore(),
                attempt.submittedAt(),
                answers
        );
    }

    /**
     * 教师提交批改。
     *
     * 支持分批批改：
     * 批改部分简答题后，剩余题目继续保持待批改。
     *
     * 同一个 attempt 加行锁，避免并发重复批改。
     */
    @Transactional(rollbackFor = Exception.class)
    public ExerciseReviewDetailVO grade(
            Long attemptId,
            Long userId,
            ExerciseGradeRequest request) {

        AttemptHeader attempt = requireAttemptForUpdate(attemptId);

        permissions.requireManage(
                attempt.courseId(),
                userId
        );

        if (!"PENDING_REVIEW".equals(attempt.status())) {
            throw new BusinessException(
                    409,
                    "当前答题记录不处于待批改状态"
            );
        }

        if (request == null
                || request.answers() == null
                || request.answers().isEmpty()) {
            throw new BusinessException(
                    400,
                    "请至少提交一道题目的批改结果"
            );
        }

        Set<Long> processedIds = new HashSet<>();

        for (ExerciseGradeRequest.AnswerGrade item
                : request.answers()) {

            if (item == null || item.answerId() == null) {
                throw new BusinessException(400, "答题记录ID不能为空");
            }

            if (!processedIds.add(item.answerId())) {
                throw new BusinessException(400, "不能重复批改同一道题");
            }

            if (item.score() == null
                    || item.score().compareTo(BigDecimal.ZERO) < 0) {
                throw new BusinessException(400, "批改得分不能小于0");
            }

            AnswerToGrade answer = requirePendingAnswer(
                    attemptId,
                    item.answerId()
            );

            if (!"SHORT_ANSWER".equals(answer.questionType())) {
                throw new BusinessException(
                        400,
                        "只有简答题需要教师批改"
                );
            }

            if (item.score().compareTo(answer.maxScore()) > 0) {
                throw new BusinessException(
                        400,
                        "批改得分不能超过题目满分：" + answer.maxScore()
                );
            }

            BigDecimal score = item.score().setScale(
                    2,
                    java.math.RoundingMode.HALF_UP
            );

            String feedback = item.feedback() == null
                    ? null
                    : item.feedback().trim();

            // 满分视为正确，部分得分或零分视为未完全正确
            int correct = score.compareTo(answer.maxScore()) >= 0
                    ? 1
                    : 0;

            int updated = jdbc.update(
                    """
                    UPDATE course_exercise_attempt_answer
                    SET score_earned = ?,
                        is_correct = ?,
                        pending_review = 0,
                        teacher_feedback = ?,
                        graded_by = ?,
                        graded_at = NOW(),
                        updated_at = NOW()
                    WHERE id = ?
                      AND attempt_id = ?
                      AND pending_review = 1
                    """,
                    score,
                    correct,
                    feedback,
                    userId,
                    item.answerId(),
                    attemptId
            );

            if (updated != 1) {
                throw new BusinessException(
                        409,
                        "该题目已经被批改，请刷新后重试"
                );
            }
        }

        // 重新统计剩余待批改题目和总得分
        Integer pendingCount = jdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM course_exercise_attempt_answer
                WHERE attempt_id = ?
                  AND pending_review = 1
                """,
                Integer.class,
                attemptId
        );

        BigDecimal earnedScore = jdbc.queryForObject(
                """
                SELECT COALESCE(SUM(score_earned), 0)
                FROM course_exercise_attempt_answer
                WHERE attempt_id = ?
                """,
                BigDecimal.class,
                attemptId
        );

        boolean completed = pendingCount == null || pendingCount == 0;

        String newStatus = completed
                ? "GRADED"
                : "PENDING_REVIEW";

        jdbc.update(
                """
                UPDATE course_exercise_attempt
                SET status = ?,
                    earned_score = ?,
                    graded_at = CASE
                        WHEN ? = 'GRADED' THEN NOW()
                        ELSE NULL
                    END,
                    updated_at = NOW()
                WHERE id = ?
                """,
                newStatus,
                earnedScore,
                newStatus,
                attemptId
        );

        return detail(attemptId, userId);
    }

    private AnswerToGrade requirePendingAnswer(
            Long attemptId,
            Long answerId) {

        List<AnswerToGrade> rows = jdbc.query(
                """
                SELECT
                    aa.id,
                    aa.pending_review,
                    q.question_type,
                    q.score AS max_score
                FROM course_exercise_attempt_answer aa
                JOIN course_exercise_question q
                    ON q.id = aa.question_id
                WHERE aa.id = ?
                  AND aa.attempt_id = ?
                FOR UPDATE
                """,
                (rs, rowNum) -> new AnswerToGrade(
                        rs.getLong("id"),
                        rs.getInt("pending_review"),
                        rs.getString("question_type"),
                        rs.getBigDecimal("max_score")
                ),
                answerId,
                attemptId
        );

        if (rows.isEmpty()) {
            throw new BusinessException(404, "答题记录不存在");
        }

        AnswerToGrade answer = rows.get(0);

        if (answer.pendingReview() != 1) {
            throw new BusinessException(
                    409,
                    "该题目已经批改，不能重复提交"
            );
        }

        return answer;
    }

    private AttemptHeader requireAttempt(Long attemptId) {

        List<AttemptHeader> rows = jdbc.query(
                """
                SELECT
                    a.id,
                    a.exercise_id,
                    a.course_id,
                    a.student_id,
                    COALESCE(NULLIF(u.nickname, ''), u.username)
                        AS student_name,
                    e.title AS exercise_title,
                    a.attempt_no,
                    a.status,
                    a.total_score,
                    a.earned_score,
                    a.submitted_at
                FROM course_exercise_attempt a
                JOIN course_exercise e
                    ON e.id = a.exercise_id
                JOIN sys_user u
                    ON u.id = a.student_id
                WHERE a.id = ?
                """,
                (rs, rowNum) -> new AttemptHeader(
                        rs.getLong("id"),
                        rs.getLong("exercise_id"),
                        rs.getLong("course_id"),
                        rs.getLong("student_id"),
                        rs.getString("student_name"),
                        rs.getString("exercise_title"),
                        rs.getInt("attempt_no"),
                        rs.getString("status"),
                        rs.getBigDecimal("total_score"),
                        rs.getBigDecimal("earned_score"),
                        toLocalDateTime(rs.getTimestamp("submitted_at"))
                ),
                attemptId
        );

        if (rows.isEmpty()) {
            throw new BusinessException(404, "答题记录不存在");
        }

        return rows.get(0);
    }

    private AttemptHeader requireAttemptForUpdate(Long attemptId) {

        List<AttemptHeader> rows = jdbc.query(
                """
                SELECT
                    a.id,
                    a.exercise_id,
                    a.course_id,
                    a.student_id,
                    COALESCE(NULLIF(u.nickname, ''), u.username)
                        AS student_name,
                    e.title AS exercise_title,
                    a.attempt_no,
                    a.status,
                    a.total_score,
                    a.earned_score,
                    a.submitted_at
                FROM course_exercise_attempt a
                JOIN course_exercise e
                    ON e.id = a.exercise_id
                JOIN sys_user u
                    ON u.id = a.student_id
                WHERE a.id = ?
                FOR UPDATE
                """,
                (rs, rowNum) -> new AttemptHeader(
                        rs.getLong("id"),
                        rs.getLong("exercise_id"),
                        rs.getLong("course_id"),
                        rs.getLong("student_id"),
                        rs.getString("student_name"),
                        rs.getString("exercise_title"),
                        rs.getInt("attempt_no"),
                        rs.getString("status"),
                        rs.getBigDecimal("total_score"),
                        rs.getBigDecimal("earned_score"),
                        toLocalDateTime(rs.getTimestamp("submitted_at"))
                ),
                attemptId
        );

        if (rows.isEmpty()) {
            throw new BusinessException(404, "答题记录不存在");
        }

        return rows.get(0);
    }

    private java.time.LocalDateTime toLocalDateTime(
            Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private record AnswerToGrade(
            Long id,
            Integer pendingReview,
            String questionType,
            BigDecimal maxScore
    ) {}

    private record AttemptHeader(
            Long id,
            Long exerciseId,
            Long courseId,
            Long studentId,
            String studentName,
            String exerciseTitle,
            Integer attemptNo,
            String status,
            BigDecimal totalScore,
            BigDecimal earnedScore,
            java.time.LocalDateTime submittedAt
    ) {}
}
