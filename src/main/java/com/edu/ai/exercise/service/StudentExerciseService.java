package com.edu.ai.exercise.service;

import com.edu.ai.common.BusinessException;
import com.edu.ai.course.service.CoursePermissionService;
import com.edu.ai.exercise.dto.ExerciseAttemptVO;
import com.edu.ai.exercise.dto.ExerciseSubmitRequest;
import com.edu.ai.exercise.dto.ExerciseSubmitResultVO;
import com.edu.ai.exercise.dto.StudentExerciseVO;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.*;

@Service
public class StudentExerciseService {

    private final JdbcTemplate jdbc;
    private final CoursePermissionService permissions;
    private final ObjectMapper objectMapper;

    public StudentExerciseService(
            JdbcTemplate jdbc,
            CoursePermissionService permissions,
            ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.permissions = permissions;
        this.objectMapper = objectMapper;
    }

    /**
     * 查询学生已加入课程中的已发布练习。
     */
    public List<StudentExerciseVO> listAvailable(
            Long studentId,
            Long courseId) {

        if (courseId != null) {
            permissions.requireActiveRead(courseId, studentId);
        }

        StringBuilder sql = new StringBuilder("""
                SELECT
                    e.id,
                    e.course_id,
                    c.name AS course_name,
                    e.title,
                    e.description,
                    e.difficulty,
                    e.estimated_minutes,
                    COUNT(DISTINCT q.id) AS question_count,

                    (
                        SELECT COUNT(*)
                        FROM course_exercise_attempt a
                        WHERE a.exercise_id = e.id
                          AND a.student_id = ?
                    ) AS attempt_count,

                    (
                        SELECT a.status
                        FROM course_exercise_attempt a
                        WHERE a.exercise_id = e.id
                          AND a.student_id = ?
                        ORDER BY a.attempt_no DESC
                        LIMIT 1
                    ) AS latest_status

                FROM course_exercise e

                JOIN course c
                  ON c.id = e.course_id

                JOIN course_student cs
                  ON cs.course_id = e.course_id
                 AND cs.student_id = ?

                LEFT JOIN course_exercise_question q
                  ON q.exercise_id = e.id

                WHERE e.status = 'PUBLISHED'
                  AND c.status = 1
                """);

        List<Object> args = new ArrayList<>();
        args.add(studentId);
        args.add(studentId);
        args.add(studentId);

        if (courseId != null) {
            sql.append(" AND e.course_id = ? ");
            args.add(courseId);
        }

        sql.append("""
                GROUP BY
                    e.id,
                    e.course_id,
                    c.name,
                    e.title,
                    e.description,
                    e.difficulty,
                    e.estimated_minutes

                ORDER BY e.published_at DESC, e.id DESC
                """);

        return jdbc.query(
                sql.toString(),
                (rs, rowNum) -> new StudentExerciseVO(
                        rs.getString("id"),
                        rs.getString("course_id"),
                        rs.getString("course_name"),
                        rs.getString("title"),
                        rs.getString("description"),
                        rs.getInt("difficulty"),
                        rs.getInt("estimated_minutes"),
                        rs.getInt("question_count"),
                        rs.getInt("attempt_count"),
                        rs.getString("latest_status"),
                        List.of()
                ),
                args.toArray()
        );
    }

    /**
     * 学生查看练习详情。
     *
     * 只返回题目和选项，不返回正确答案、答案解析。
     */
    public StudentExerciseVO detail(
            Long exerciseId,
            Long studentId) {

        ExerciseHeader header = requirePublishedExercise(exerciseId);

        permissions.requireActiveRead(
                header.courseId(),
                studentId
        );

        List<StudentExerciseVO.Question> questions =
                jdbc.query(
                        """
                        SELECT
                            q.id,
                            q.knowledge_point_id,
                            kp.title AS knowledge_point_title,
                            q.question_type,
                            q.stem,
                            q.options_json,
                            q.score,
                            q.sort_order

                        FROM course_exercise_question q

                        JOIN course_knowledge_point kp
                          ON kp.id = q.knowledge_point_id
                         AND kp.course_id = ?

                        WHERE q.exercise_id = ?

                        ORDER BY q.sort_order, q.id
                        """,
                        (rs, rowNum) -> new StudentExerciseVO.Question(
                                rs.getString("id"),
                                rs.getString("knowledge_point_id"),
                                rs.getString("knowledge_point_title"),
                                rs.getString("question_type"),
                                rs.getString("stem"),
                                parseOptions(rs.getString("options_json")),
                                rs.getBigDecimal("score"),
                                rs.getInt("sort_order")
                        ),
                        header.courseId(),
                        exerciseId
                );

        List<StudentExerciseVO> cards = jdbc.query(
                """
                SELECT
                    e.id,
                    e.course_id,
                    c.name AS course_name,
                    e.title,
                    e.description,
                    e.difficulty,
                    e.estimated_minutes,

                    (SELECT COUNT(*)
                     FROM course_exercise_question q
                     WHERE q.exercise_id = e.id) AS question_count,

                    (SELECT COUNT(*)
                     FROM course_exercise_attempt a
                     WHERE a.exercise_id = e.id
                       AND a.student_id = ?) AS attempt_count,

                    (SELECT a.status
                     FROM course_exercise_attempt a
                     WHERE a.exercise_id = e.id
                       AND a.student_id = ?
                     ORDER BY a.attempt_no DESC
                     LIMIT 1) AS latest_status

                FROM course_exercise e
                JOIN course c ON c.id = e.course_id
                WHERE e.id = ?
                """,
                (rs, rowNum) -> new StudentExerciseVO(
                        rs.getString("id"),
                        rs.getString("course_id"),
                        rs.getString("course_name"),
                        rs.getString("title"),
                        rs.getString("description"),
                        rs.getInt("difficulty"),
                        rs.getInt("estimated_minutes"),
                        rs.getInt("question_count"),
                        rs.getInt("attempt_count"),
                        rs.getString("latest_status"),
                        questions
                ),
                studentId,
                studentId,
                exerciseId
        );

        if (cards.isEmpty()) {
            throw new BusinessException(404, "练习不存在");
        }

        return cards.get(0);
    }

    /**
     * 开始一次练习。
     *
     * 对练习主记录加锁，避免同一个学生并发创建多个进行中的答题记录。
     * 已有进行中的记录时直接返回，不重复创建。
     */
    @Transactional(rollbackFor = Exception.class)
    public ExerciseAttemptVO startAttempt(
            Long exerciseId,
            Long studentId) {

        ExerciseHeader header = requirePublishedExerciseForUpdate(
                exerciseId
        );

        permissions.requireActiveRead(
                header.courseId(),
                studentId
        );

        List<AttemptHeader> active = jdbc.query(
                """
                SELECT
                    id,
                    exercise_id,
                    attempt_no,
                    status,
                    total_score,
                    earned_score,
                    started_at,
                    submitted_at
                FROM course_exercise_attempt
                WHERE exercise_id = ?
                  AND student_id = ?
                  AND status = 'IN_PROGRESS'
                ORDER BY attempt_no DESC
                LIMIT 1
                FOR UPDATE
                """,
                (rs, rowNum) -> mapAttempt(rs),
                exerciseId,
                studentId
        );

        if (!active.isEmpty()) {
            return toAttemptVO(active.get(0));
        }

        Integer nextAttemptNo = jdbc.queryForObject(
                """
                SELECT COALESCE(MAX(attempt_no), 0) + 1
                FROM course_exercise_attempt
                WHERE exercise_id = ?
                  AND student_id = ?
                """,
                Integer.class,
                exerciseId,
                studentId
        );

        BigDecimal totalScore = jdbc.queryForObject(
                """
                SELECT COALESCE(SUM(score), 0)
                FROM course_exercise_question
                WHERE exercise_id = ?
                """,
                BigDecimal.class,
                exerciseId
        );

        if (totalScore == null || totalScore.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(400, "当前练习没有有效题目");
        }

        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    """
                    INSERT INTO course_exercise_attempt
                        (exercise_id, course_id, student_id,
                         attempt_no, status, total_score, earned_score,
                         started_at, created_at, updated_at)
                    VALUES (?, ?, ?, ?, 'IN_PROGRESS', ?, 0,
                            NOW(), NOW(), NOW())
                    """,
                    Statement.RETURN_GENERATED_KEYS
            );

            ps.setLong(1, exerciseId);
            ps.setLong(2, header.courseId());
            ps.setLong(3, studentId);
            ps.setInt(4, nextAttemptNo);
            ps.setBigDecimal(5, totalScore);

            return ps;
        }, keyHolder);

        Number generatedId = keyHolder.getKey();

        if (generatedId == null) {
            throw new IllegalStateException("创建答题记录失败");
        }

        return getAttempt(
                generatedId.longValue(),
                studentId
        );
    }

    /**
     * 提交学生答案并自动判分。
     *
     * 单选、多选、判断：自动判分。
     * 简答题：进入待教师批改状态。
     *
     * 重复提交时返回第一次提交结果，不覆盖历史答案。
     */
    @Transactional(rollbackFor = Exception.class)
    public ExerciseSubmitResultVO submit(
            Long attemptId,
            Long studentId,
            ExerciseSubmitRequest request) {

        List<AttemptHeader> attempts = jdbc.query(
                """
                SELECT
                    id,
                    exercise_id,
                    course_id,
                    student_id,
                    attempt_no,
                    status,
                    total_score,
                    earned_score,
                    started_at,
                    submitted_at
                FROM course_exercise_attempt
                WHERE id = ?
                FOR UPDATE
                """,
                (rs, rowNum) -> new AttemptHeader(
                        rs.getLong("id"),
                        rs.getLong("exercise_id"),
                        rs.getLong("course_id"),
                        rs.getLong("student_id"),
                        rs.getInt("attempt_no"),
                        rs.getString("status"),
                        rs.getBigDecimal("total_score"),
                        rs.getBigDecimal("earned_score"),
                        toLocalDateTime(rs.getTimestamp("started_at")),
                        toLocalDateTime(rs.getTimestamp("submitted_at"))
                ),
                attemptId
        );

        if (attempts.isEmpty()) {
            throw new BusinessException(404, "答题记录不存在");
        }

        AttemptHeader attempt = attempts.get(0);

        if (!attempt.studentId().equals(studentId)) {
            throw new BusinessException(403, "无权提交该答题记录");
        }

        permissions.requireActiveRead(
                attempt.courseId(),
                studentId
        );

        // 幂等：已经提交过，不再重复判分或覆盖答案
        if (!"IN_PROGRESS".equals(attempt.status())) {
            return getResult(attemptId, studentId);
        }

        List<QuestionData> questions = jdbc.query(
                """
                SELECT
                    id,
                    question_type,
                    correct_answer,
                    explanation,
                    score,
                    stem
                FROM course_exercise_question
                WHERE exercise_id = ?
                ORDER BY sort_order, id
                """,
                (rs, rowNum) -> new QuestionData(
                        rs.getLong("id"),
                        rs.getString("question_type"),
                        rs.getString("correct_answer"),
                        rs.getString("explanation"),
                        rs.getBigDecimal("score"),
                        rs.getString("stem")
                ),
                attempt.exerciseId()
        );

        if (questions.isEmpty()) {
            throw new BusinessException(400, "练习没有题目，无法提交");
        }

        Map<Long, String> answerMap = new HashMap<>();

        if (request != null && request.answers() != null) {
            for (ExerciseSubmitRequest.AnswerRequest answer
                    : request.answers()) {

                if (answer == null || answer.questionId() == null) {
                    throw new BusinessException(400, "答案中的题目ID不能为空");
                }

                if (answerMap.containsKey(answer.questionId())) {
                    throw new BusinessException(400, "同一道题不能重复提交答案");
                }

                answerMap.put(
                        answer.questionId(),
                        answer.answer() == null ? "" : answer.answer().trim()
                );
            }
        }

        Set<Long> allowedQuestionIds = new HashSet<>();

        for (QuestionData question : questions) {
            allowedQuestionIds.add(question.id());
        }

        if (!allowedQuestionIds.containsAll(answerMap.keySet())) {
            throw new BusinessException(400, "提交的答案中包含不属于当前练习的题目");
        }

        BigDecimal earnedTotal = BigDecimal.ZERO;
        int pendingReviewCount = 0;

        for (QuestionData question : questions) {

            String studentAnswer = answerMap.getOrDefault(
                    question.id(),
                    ""
            );

            boolean shortAnswer =
                    "SHORT_ANSWER".equals(question.questionType());

            Boolean correct = null;
            BigDecimal earned = BigDecimal.ZERO;
            int pendingReview = 0;

            if (shortAnswer) {

                // 简答题暂不自动判分
                pendingReview = 1;
                pendingReviewCount++;

            } else {

                correct = isCorrect(
                        question.questionType(),
                        studentAnswer,
                        question.correctAnswer()
                );

                if (correct) {
                    earned = question.score();
                }
            }

            earnedTotal = earnedTotal.add(earned);

            jdbc.update(
                    """
                    INSERT INTO course_exercise_attempt_answer
                        (attempt_id, question_id, student_answer,
                         is_correct, score_earned, pending_review,
                         created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, NOW(), NOW())
                    """,
                    attemptId,
                    question.id(),
                    studentAnswer,
                    correct,
                    earned,
                    pendingReview
            );
        }

        String finalStatus = pendingReviewCount > 0
                ? "PENDING_REVIEW"
                : "GRADED";

        jdbc.update(
                """
                UPDATE course_exercise_attempt
                SET status = ?,
                    earned_score = ?,
                    submitted_at = NOW(),
                    graded_at = CASE
                        WHEN ? = 'GRADED' THEN NOW()
                        ELSE NULL
                    END,
                    updated_at = NOW()
                WHERE id = ?
                  AND status = 'IN_PROGRESS'
                """,
                finalStatus,
                earnedTotal,
                finalStatus,
                attemptId
        );

        return getResult(attemptId, studentId);
    }

    /**
     * 学生查询自己的答题结果。
     */
    public ExerciseSubmitResultVO getResult(
            Long attemptId,
            Long studentId) {

        List<AttemptHeader> attempts = jdbc.query(
                """
                SELECT
                    id,
                    exercise_id,
                    course_id,
                    student_id,
                    attempt_no,
                    status,
                    total_score,
                    earned_score,
                    started_at,
                    submitted_at
                FROM course_exercise_attempt
                WHERE id = ?
                  AND student_id = ?
                """,
                (rs, rowNum) -> new AttemptHeader(
                        rs.getLong("id"),
                        rs.getLong("exercise_id"),
                        rs.getLong("course_id"),
                        rs.getLong("student_id"),
                        rs.getInt("attempt_no"),
                        rs.getString("status"),
                        rs.getBigDecimal("total_score"),
                        rs.getBigDecimal("earned_score"),
                        toLocalDateTime(rs.getTimestamp("started_at")),
                        toLocalDateTime(rs.getTimestamp("submitted_at"))
                ),
                attemptId,
                studentId
        );

        if (attempts.isEmpty()) {
            throw new BusinessException(404, "答题记录不存在");
        }

        AttemptHeader attempt = attempts.get(0);

        permissions.requireActiveRead(
                attempt.courseId(),
                studentId
        );

        List<ExerciseSubmitResultVO.AnswerResult> answers =
                jdbc.query(
                        """
                        SELECT
                            a.question_id,
                            q.question_type,
                            q.stem,
                            a.student_answer,
                            a.is_correct,
                            q.correct_answer,
                            q.explanation,
                            q.score,
                            a.score_earned,
                            a.pending_review

                        FROM course_exercise_attempt_answer a

                        JOIN course_exercise_question q
                          ON q.id = a.question_id

                        WHERE a.attempt_id = ?

                        ORDER BY q.sort_order, q.id
                        """,
                        (rs, rowNum) -> {

                            boolean pending =
                                    rs.getInt("pending_review") == 1;

                            String type =
                                    rs.getString("question_type");

                            boolean shortAnswer =
                                    "SHORT_ANSWER".equals(type);

                            Object correctValue =
                                    rs.getObject("is_correct");

                            Boolean correct = correctValue == null
                                    ? null
                                    : rs.getInt("is_correct") == 1;

                            // 简答题在教师批改前不泄露参考答案
                            String correctAnswer =
                                    shortAnswer && pending
                                            ? null
                                            : rs.getString("correct_answer");

                            String explanation =
                                    shortAnswer && pending
                                            ? null
                                            : rs.getString("explanation");

                            return new ExerciseSubmitResultVO.AnswerResult(
                                    rs.getString("question_id"),
                                    type,
                                    rs.getString("stem"),
                                    rs.getString("student_answer"),
                                    correct,
                                    correctAnswer,
                                    explanation,
                                    rs.getBigDecimal("score"),
                                    rs.getBigDecimal("score_earned"),
                                    pending
                            );
                        },
                        attemptId
                );

        int pendingCount = (int) answers.stream()
                .filter(ExerciseSubmitResultVO.AnswerResult::pendingReview)
                .count();

        return new ExerciseSubmitResultVO(
                attempt.id().toString(),
                attempt.exerciseId().toString(),
                attempt.attemptNo(),
                attempt.status(),
                attempt.totalScore(),
                attempt.earnedScore(),
                pendingCount,
                attempt.submittedAt(),
                answers
        );
    }

    /**
     * 查询答题记录。
     */
    private ExerciseAttemptVO getAttempt(
            Long attemptId,
            Long studentId) {

        List<AttemptHeader> rows = jdbc.query(
                """
                SELECT
                    id,
                    exercise_id,
                    attempt_no,
                    status,
                    total_score,
                    earned_score,
                    started_at,
                    submitted_at
                FROM course_exercise_attempt
                WHERE id = ?
                  AND student_id = ?
                """,
                (rs, rowNum) -> mapAttempt(rs),
                attemptId,
                studentId
        );

        if (rows.isEmpty()) {
            throw new BusinessException(404, "答题记录不存在");
        }

        return toAttemptVO(rows.get(0));
    }

    private ExerciseHeader requirePublishedExercise(Long exerciseId) {

        List<ExerciseHeader> rows = jdbc.query(
                """
                SELECT id, course_id, status
                FROM course_exercise
                WHERE id = ?
                """,
                (rs, rowNum) -> new ExerciseHeader(
                        rs.getLong("id"),
                        rs.getLong("course_id"),
                        rs.getString("status")
                ),
                exerciseId
        );

        if (rows.isEmpty()
                || !"PUBLISHED".equals(rows.get(0).status())) {
            throw new BusinessException(404, "练习不存在或已关闭");
        }

        return rows.get(0);
    }

    /**
     * 开始练习时锁定练习主记录。
     */
    private ExerciseHeader requirePublishedExerciseForUpdate(
            Long exerciseId) {

        List<ExerciseHeader> rows = jdbc.query(
                """
                SELECT id, course_id, status
                FROM course_exercise
                WHERE id = ?
                FOR UPDATE
                """,
                (rs, rowNum) -> new ExerciseHeader(
                        rs.getLong("id"),
                        rs.getLong("course_id"),
                        rs.getString("status")
                ),
                exerciseId
        );

        if (rows.isEmpty()
                || !"PUBLISHED".equals(rows.get(0).status())) {
            throw new BusinessException(404, "练习不存在或已关闭");
        }

        return rows.get(0);
    }

    /**
     * 根据 JSON 解析选择题选项。
     */
    private List<StudentExerciseVO.Option> parseOptions(
            String optionsJson) {

        if (optionsJson == null || optionsJson.isBlank()) {
            return List.of();
        }

        try {
            JsonNode root = objectMapper.readTree(optionsJson);

            if (root == null || !root.isArray()) {
                return List.of();
            }

            List<StudentExerciseVO.Option> options =
                    new ArrayList<>();

            for (JsonNode node : root) {
                options.add(
                        new StudentExerciseVO.Option(
                                node.path("label").asText(""),
                                node.path("content").asText("")
                        )
                );
            }

            return options;

        } catch (Exception e) {
            throw new BusinessException(500, "题目选项数据格式异常");
        }
    }

    /**
     * 自动判分。
     */
    private boolean isCorrect(
            String type,
            String studentAnswer,
            String correctAnswer) {

        if (studentAnswer == null || studentAnswer.isBlank()) {
            return false;
        }

        if (correctAnswer == null || correctAnswer.isBlank()) {
            return false;
        }

        return switch (type) {

            case "SINGLE_CHOICE" ->
                    normalizeSingle(studentAnswer)
                            .equals(normalizeSingle(correctAnswer));

            case "MULTIPLE_CHOICE" ->
                    normalizeMultiple(studentAnswer)
                            .equals(normalizeMultiple(correctAnswer));

            case "TRUE_FALSE" ->
                    normalizeBoolean(studentAnswer)
                            .equals(normalizeBoolean(correctAnswer));

            default -> false;
        };
    }

    private String normalizeSingle(String answer) {
        return answer.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * 多选题不区分选项顺序。
     * A,C 与 C,A 视为相同答案。
     */
    private Set<String> normalizeMultiple(String answer) {

        Set<String> result = new TreeSet<>();

        String normalized = answer
                .trim()
                .toUpperCase(Locale.ROOT)
                .replace('，', ',')
                .replace('；', ',')
                .replace(';', ',');

        for (String item : normalized.split("[,\\s]+")) {
            if (!item.isBlank()) {
                result.add(item.trim());
            }
        }

        return result;
    }

    private String normalizeBoolean(String answer) {

        String value = answer.trim().toUpperCase(Locale.ROOT);

        if (Set.of("TRUE", "正确", "对", "1").contains(value)) {
            return "TRUE";
        }

        if (Set.of("FALSE", "错误", "错", "0").contains(value)) {
            return "FALSE";
        }

        return value;
    }

    private AttemptHeader mapAttempt(
            java.sql.ResultSet rs) throws java.sql.SQLException {

        return new AttemptHeader(
                rs.getLong("id"),
                rs.getLong("exercise_id"),
                0L,
                0L,
                rs.getInt("attempt_no"),
                rs.getString("status"),
                rs.getBigDecimal("total_score"),
                rs.getBigDecimal("earned_score"),
                toLocalDateTime(rs.getTimestamp("started_at")),
                toLocalDateTime(rs.getTimestamp("submitted_at"))
        );
    }

    private ExerciseAttemptVO toAttemptVO(AttemptHeader attempt) {

        return new ExerciseAttemptVO(
                attempt.id().toString(),
                attempt.exerciseId().toString(),
                attempt.attemptNo(),
                attempt.status(),
                attempt.totalScore(),
                attempt.earnedScore(),
                attempt.startedAt(),
                attempt.submittedAt()
        );
    }

    private java.time.LocalDateTime toLocalDateTime(
            Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private record ExerciseHeader(
            Long id,
            Long courseId,
            String status
    ) {}

    private record AttemptHeader(
            Long id,
            Long exerciseId,
            Long courseId,
            Long studentId,
            Integer attemptNo,
            String status,
            BigDecimal totalScore,
            BigDecimal earnedScore,
            java.time.LocalDateTime startedAt,
            java.time.LocalDateTime submittedAt
    ) {}

    private record QuestionData(
            Long id,
            String questionType,
            String correctAnswer,
            String explanation,
            BigDecimal score,
            String stem
    ) {}
}
