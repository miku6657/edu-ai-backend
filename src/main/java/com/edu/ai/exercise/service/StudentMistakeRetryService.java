package com.edu.ai.exercise.service;

import com.edu.ai.common.BusinessException;
import com.edu.ai.course.service.CoursePermissionService;
import com.edu.ai.exercise.dto.StudentRetryAttemptVO;
import com.edu.ai.exercise.dto.StudentRetryDetailVO;
import com.edu.ai.exercise.dto.StudentRetryListVO;
import com.edu.ai.exercise.dto.StudentRetryResultVO;
import com.edu.ai.exercise.dto.StudentRetrySelfReviewRequest;
import com.edu.ai.exercise.dto.StudentRetrySubmitRequest;

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
import java.time.LocalDateTime;
import java.util.*;

@Service
public class StudentMistakeRetryService {

    private final JdbcTemplate jdbc;
    private final CoursePermissionService permissions;
    private final ObjectMapper objectMapper;

    public StudentMistakeRetryService(
            JdbcTemplate jdbc,
            CoursePermissionService permissions,
            ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.permissions = permissions;
        this.objectMapper = objectMapper;
    }

    /**
     * 查询当前学生的重练清单。
     */
    public List<StudentRetryListVO> listLists(Long studentId) {

        return jdbc.query(
                """
                SELECT
                    rl.id,
                    rl.title,
                    rl.item_count,
                    rl.status,
                    rl.created_at,

                    (
                        SELECT COUNT(*)
                        FROM course_exercise_retry_attempt a
                        WHERE a.retry_list_id = rl.id
                          AND a.student_id = rl.student_id
                    ) AS attempt_count,

                    (
                        SELECT a.status
                        FROM course_exercise_retry_attempt a
                        WHERE a.retry_list_id = rl.id
                          AND a.student_id = rl.student_id
                        ORDER BY a.attempt_no DESC
                        LIMIT 1
                    ) AS latest_attempt_status

                FROM course_exercise_retry_list rl

                WHERE rl.student_id = ?

                ORDER BY rl.created_at DESC, rl.id DESC
                """,
                (rs, rowNum) -> new StudentRetryListVO(
                        rs.getString("id"),
                        rs.getString("title"),
                        rs.getInt("item_count"),
                        rs.getString("status"),
                        rs.getInt("attempt_count"),
                        rs.getString("latest_attempt_status"),
                        toLocalDateTime(rs.getTimestamp("created_at"))
                ),
                studentId
        );
    }

    /**
     * 查看重练清单详情。
     */
    public StudentRetryDetailVO detail(
            Long retryListId,
            Long studentId) {

        RetryListHeader header = requireOwnedList(
                retryListId,
                studentId,
                false
        );

        List<RetryItemData> items = loadRetryItems(
                retryListId,
                studentId
        );

        validateItemCount(header, items);

        return new StudentRetryDetailVO(
                header.id().toString(),
                header.title(),
                items.size(),
                items.stream()
                        .map(item -> new StudentRetryDetailVO.Item(
                                item.retryItemId().toString(),
                                item.sourceAnswerId().toString(),
                                item.questionId().toString(),
                                item.courseName(),
                                item.exerciseTitle(),
                                item.knowledgePointTitle(),
                                item.questionType(),
                                item.stem(),
                                item.options(),
                                item.score(),
                                item.sortOrder()
                        ))
                        .toList()
        );
    }

    /**
     * 开始重练。
     *
     * 优先恢复：
     * 1. IN_PROGRESS：继续未提交的答题。
     * 2. PENDING_SELF_REVIEW：继续简答题核对。
     * 3. GRADED：允许创建新一轮。
     *
     * 对清单主记录加锁，避免并发创建重复答题记录。
     */
    @Transactional(rollbackFor = Exception.class)
    public StudentRetryAttemptVO startAttempt(
            Long retryListId,
            Long studentId) {

        RetryListHeader list = requireOwnedList(
                retryListId,
                studentId,
                true
        );

        List<RetryItemData> items = loadRetryItems(
                retryListId,
                studentId
        );

        validateItemCount(list, items);

        if (items.isEmpty()) {
            throw new BusinessException(400, "重练清单没有可用题目");
        }

        List<AttemptHeader> active = findAttempt(
                retryListId,
                studentId,
                "IN_PROGRESS"
        );

        if (!active.isEmpty()) {
            return toAttemptVO(active.get(0));
        }

        List<AttemptHeader> pendingReview = findAttempt(
                retryListId,
                studentId,
                "PENDING_SELF_REVIEW"
        );

        if (!pendingReview.isEmpty()) {
            return toAttemptVO(pendingReview.get(0));
        }

        Integer nextAttemptNo = jdbc.queryForObject(
                """
                SELECT COALESCE(MAX(attempt_no), 0) + 1
                FROM course_exercise_retry_attempt
                WHERE retry_list_id = ?
                  AND student_id = ?
                """,
                Integer.class,
                retryListId,
                studentId
        );

        BigDecimal totalScore = items.stream()
                .map(RetryItemData::score)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        KeyHolder keyHolder = new GeneratedKeyHolder();

        int inserted = jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    """
                    INSERT INTO course_exercise_retry_attempt
                        (retry_list_id, student_id, attempt_no,
                         status, total_score, earned_score,
                         started_at, created_at, updated_at)
                    VALUES (?, ?, ?, 'IN_PROGRESS', ?, 0,
                            NOW(), NOW(), NOW())
                    """,
                    Statement.RETURN_GENERATED_KEYS
            );

            ps.setLong(1, retryListId);
            ps.setLong(2, studentId);
            ps.setInt(3, nextAttemptNo);
            ps.setBigDecimal(4, totalScore);

            return ps;
        }, keyHolder);

        Number generatedId = keyHolder.getKey();

        if (inserted != 1 || generatedId == null) {
            throw new IllegalStateException("创建重练记录失败");
        }

        return getAttempt(
                generatedId.longValue(),
                studentId
        );
    }

    /**
     * 提交重练答案。
     *
     * 客观题自动判分。
     * 简答题进入学生自我核对状态。
     *
     * 重复提交时返回第一次提交结果。
     */
    @Transactional(rollbackFor = Exception.class)
    public StudentRetryResultVO submit(
            Long attemptId,
            Long studentId,
            StudentRetrySubmitRequest request) {

        AttemptHeader attempt = requireAttemptForUpdate(
                attemptId,
                studentId
        );

        if (!"IN_PROGRESS".equals(attempt.status())) {
            return getResult(attemptId, studentId);
        }

        List<RetryItemData> items = loadRetryItems(
                attempt.retryListId(),
                studentId
        );

        Map<Long, RetryItemData> itemMap = new LinkedHashMap<>();

        for (RetryItemData item : items) {
            itemMap.put(item.retryItemId(), item);
        }

        Map<Long, String> answerMap = new HashMap<>();

        if (request != null && request.answers() != null) {

            for (StudentRetrySubmitRequest.AnswerRequest answer
                    : request.answers()) {

                if (answer == null || answer.retryItemId() == null) {
                    throw new BusinessException(400, "重练题目ID不能为空");
                }

                if (answerMap.containsKey(answer.retryItemId())) {
                    throw new BusinessException(400, "不能重复提交同一道题");
                }

                if (!itemMap.containsKey(answer.retryItemId())) {
                    throw new BusinessException(400, "提交的题目不属于当前重练清单");
                }

                answerMap.put(
                        answer.retryItemId(),
                        answer.answer() == null
                                ? ""
                                : answer.answer().trim()
                );
            }
        }

        BigDecimal earnedTotal = BigDecimal.ZERO;
        int pendingCount = 0;

        for (RetryItemData item : items) {

            String studentAnswer = answerMap.getOrDefault(
                    item.retryItemId(),
                    ""
            );

            boolean shortAnswer =
                    "SHORT_ANSWER".equals(item.questionType());

            Boolean correct;
            BigDecimal earned = BigDecimal.ZERO;
            int pending = 0;

            if (shortAnswer) {
                correct = null;
                pending = 1;
                pendingCount++;
            } else {
                correct = isCorrect(
                        item.questionType(),
                        studentAnswer,
                        item.correctAnswer()
                );

                if (correct) {
                    earned = item.score();
                }
            }

            earnedTotal = earnedTotal.add(earned);

            jdbc.update(
                    """
                    INSERT INTO course_exercise_retry_attempt_answer
                        (attempt_id, retry_item_id, question_id,
                         student_answer, is_correct, score_earned,
                         pending_self_review, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
                    """,
                    attemptId,
                    item.retryItemId(),
                    item.questionId(),
                    studentAnswer,
                    correct,
                    earned,
                    pending
            );
        }

        String status = pendingCount > 0
                ? "PENDING_SELF_REVIEW"
                : "GRADED";

        jdbc.update(
                """
                UPDATE course_exercise_retry_attempt
                SET status = ?,
                    earned_score = ?,
                    submitted_at = NOW(),
                    graded_at = CASE
                        WHEN ? = 'GRADED' THEN NOW()
                        ELSE NULL
                    END,
                    updated_at = NOW()
                WHERE id = ?
                  AND student_id = ?
                  AND status = 'IN_PROGRESS'
                """,
                status,
                earnedTotal,
                status,
                attemptId,
                studentId
        );

        if ("GRADED".equals(status)) {
            markRetryListCompleted(attempt.retryListId(), studentId);
        }

        return getResult(attemptId, studentId);
    }

    /**
     * 查询重练结果。
     */
    public StudentRetryResultVO getResult(
            Long attemptId,
            Long studentId) {

        AttemptHeader attempt = requireAttempt(
                attemptId,
                studentId
        );

        List<RetryItemData> items = loadRetryItems(
                attempt.retryListId(),
                studentId
        );

        validateItemCount(
                requireOwnedList(
                        attempt.retryListId(),
                        studentId,
                        false
                ),
                items
        );

        List<StudentRetryResultVO.AnswerResult> answers =
                jdbc.query(
                        """
                        SELECT
                            aa.retry_item_id,
                            aa.question_id,
                            q.question_type,
                            q.stem,
                            aa.student_answer,
                            aa.is_correct,
                            q.correct_answer,
                            q.explanation,
                            q.score,
                            aa.score_earned,
                            aa.self_review_understood,
                            aa.pending_self_review

                        FROM course_exercise_retry_attempt_answer aa

                        JOIN course_exercise_question q
                          ON q.id = aa.question_id

                        WHERE aa.attempt_id = ?

                        ORDER BY aa.retry_item_id
                        """,
                        (rs, rowNum) -> {

                            Object correctValue =
                                    rs.getObject("is_correct");

                            Boolean correct = correctValue == null
                                    ? null
                                    : rs.getInt("is_correct") == 1;

                            boolean pending =
                                    rs.getInt("pending_self_review") == 1;

                            Object reviewValue = rs.getObject("self_review_understood");

                            Boolean selfReviewUnderstood = reviewValue == null
                                    ? null
                                    : rs.getInt("self_review_understood") == 1;

                            return new StudentRetryResultVO.AnswerResult(
                                    rs.getString("retry_item_id"),
                                    rs.getString("question_id"),
                                    rs.getString("question_type"),
                                    rs.getString("stem"),
                                    rs.getString("student_answer"),
                                    correct,
                                    rs.getString("correct_answer"),
                                    rs.getString("explanation"),
                                    rs.getBigDecimal("score"),
                                    rs.getBigDecimal("score_earned"),
                                    selfReviewUnderstood,
                                    pending
                            );
                        },
                        attemptId
                );

        int pendingCount = (int) answers.stream()
                .filter(StudentRetryResultVO.AnswerResult::pendingSelfReview)
                .count();

        return new StudentRetryResultVO(
                attempt.id().toString(),
                attempt.retryListId().toString(),
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
     * 学生完成简答题自我核对。
     *
     * 已掌握：本题计满分。
     * 仍需巩固：本题计0分。
     *
     * 全部核对后，重练状态变为 GRADED。
     */
    @Transactional(rollbackFor = Exception.class)
    public StudentRetryResultVO selfReview(
            Long attemptId,
            Long studentId,
            StudentRetrySelfReviewRequest request) {

        AttemptHeader attempt = requireAttemptForUpdate(
                attemptId,
                studentId
        );

        if ("GRADED".equals(attempt.status())) {
            return getResult(attemptId, studentId);
        }

        if (!"PENDING_SELF_REVIEW".equals(attempt.status())) {
            throw new BusinessException(
                    400,
                    "当前重练记录不需要自我核对"
            );
        }

        List<RetryItemData> items = loadRetryItems(
                attempt.retryListId(),
                studentId
        );

        Map<Long, RetryItemData> itemMap = new HashMap<>();

        for (RetryItemData item : items) {
            itemMap.put(item.retryItemId(), item);
        }

        List<Long> pendingIds = jdbc.query(
                """
                SELECT retry_item_id
                FROM course_exercise_retry_attempt_answer
                WHERE attempt_id = ?
                  AND pending_self_review = 1
                """,
                (rs, rowNum) -> rs.getLong("retry_item_id"),
                attemptId
        );

        Map<Long, Boolean> reviewMap = new LinkedHashMap<>();

        for (StudentRetrySelfReviewRequest.ReviewItem answer
                : request.answers()) {

            if (answer == null
                    || answer.retryItemId() == null
                    || answer.understood() == null) {
                throw new BusinessException(400, "自我核对内容不完整");
            }

            if (reviewMap.put(
                    answer.retryItemId(),
                    answer.understood()
            ) != null) {
                throw new BusinessException(400, "不能重复核对同一道题");
            }

            if (!itemMap.containsKey(answer.retryItemId())) {
                throw new BusinessException(400, "题目不属于当前重练清单");
            }
        }

        if (reviewMap.size() != pendingIds.size()
                || !reviewMap.keySet().containsAll(pendingIds)) {
            throw new BusinessException(
                    400,
                    "请完成全部待核对简答题"
            );
        }

        for (Long retryItemId : pendingIds) {

            boolean understood = reviewMap.get(retryItemId);

            int updated = jdbc.update(
                    """
                    UPDATE course_exercise_retry_attempt_answer
                    SET self_review_understood = ?,
                        pending_self_review = 0,
                        self_reviewed_at = NOW(),
                        updated_at = NOW()
                    WHERE attempt_id = ?
                      AND retry_item_id = ?
                      AND pending_self_review = 1
                    """,
                    understood,
                    attemptId,
                    retryItemId
            );

            if (updated != 1) {
                throw new BusinessException(
                        409,
                        "简答题核对状态已经发生变化，请刷新后重试"
                );
            }
        }

        Integer remaining = jdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM course_exercise_retry_attempt_answer
                WHERE attempt_id = ?
                  AND pending_self_review = 1
                """,
                Integer.class,
                attemptId
        );

        if (remaining == null || remaining > 0) {
            throw new BusinessException(409, "仍有简答题没有完成核对");
        }

        jdbc.update(
                """
                UPDATE course_exercise_retry_attempt
                SET status = 'GRADED',
                    graded_at = NOW(),
                    updated_at = NOW()
                WHERE id = ?
                  AND student_id = ?
                  AND status = 'PENDING_SELF_REVIEW'
                """,
                attemptId,
                studentId
        );

        markRetryListCompleted(attempt.retryListId(), studentId);

        return getResult(attemptId, studentId);
    }

    // =====================================================
    // 内部查询方法
    // =====================================================

    private RetryListHeader requireOwnedList(
            Long retryListId,
            Long studentId,
            boolean forUpdate) {

        String sql = """
                SELECT id, student_id, title, item_count, status
                FROM course_exercise_retry_list
                WHERE id = ?
                  AND student_id = ?
                """ + (forUpdate ? " FOR UPDATE" : "");

        List<RetryListHeader> rows = jdbc.query(
                sql,
                (rs, rowNum) -> new RetryListHeader(
                        rs.getLong("id"),
                        rs.getLong("student_id"),
                        rs.getString("title"),
                        rs.getInt("item_count"),
                        rs.getString("status")
                ),
                retryListId,
                studentId
        );

        if (rows.isEmpty()) {
            throw new BusinessException(404, "重练清单不存在或无权访问");
        }

        return rows.get(0);
    }

    private List<RetryItemData> loadRetryItems(
            Long retryListId,
            Long studentId) {

        List<RetryItemData> items = jdbc.query(
                """
                SELECT
                    ri.id AS retry_item_id,
                    ri.source_answer_id,
                    ri.question_id,
                    ri.sort_order,

                    q.question_type,
                    q.stem,
                    q.options_json,
                    q.correct_answer,
                    q.explanation,
                    q.score,

                    e.title AS exercise_title,

                    c.id AS course_id,
                    c.name AS course_name,

                    kp.title AS knowledge_point_title

                FROM course_exercise_retry_item ri

                JOIN course_exercise_retry_list rl
                  ON rl.id = ri.retry_list_id

                JOIN course_exercise_question q
                  ON q.id = ri.question_id

                JOIN course_exercise e
                  ON e.id = q.exercise_id

                JOIN course c
                  ON c.id = e.course_id

                JOIN course_student cs
                  ON cs.course_id = c.id
                 AND cs.student_id = rl.student_id

                LEFT JOIN course_knowledge_point kp
                  ON kp.id = q.knowledge_point_id
                 AND kp.course_id = c.id

                WHERE ri.retry_list_id = ?
                  AND rl.student_id = ?
                  AND c.status = 1

                ORDER BY ri.sort_order, ri.id
                """,
                (rs, rowNum) -> new RetryItemData(
                        rs.getLong("retry_item_id"),
                        rs.getLong("source_answer_id"),
                        rs.getLong("question_id"),
                        rs.getLong("course_id"),
                        rs.getString("course_name"),
                        rs.getString("exercise_title"),
                        rs.getString("knowledge_point_title"),
                        rs.getString("question_type"),
                        rs.getString("stem"),
                        parseOptions(rs.getString("options_json")),
                        rs.getString("correct_answer"),
                        rs.getString("explanation"),
                        rs.getBigDecimal("score"),
                        rs.getInt("sort_order")
                ),
                retryListId,
                studentId
        );

        Set<Long> courseIds = new HashSet<>();

        for (RetryItemData item : items) {
            courseIds.add(item.courseId());
        }

        for (Long courseId : courseIds) {
            permissions.requireActiveRead(courseId, studentId);
        }

        return items;
    }

    private void validateItemCount(
            RetryListHeader header,
            List<RetryItemData> items) {

        if (items.size() != header.itemCount()) {
            throw new BusinessException(
                    409,
                    "重练清单中的部分题目已经不可用，请检查原练习"
            );
        }
    }

    private List<AttemptHeader> findAttempt(
            Long retryListId,
            Long studentId,
            String status) {

        return jdbc.query(
                """
                SELECT
                    id,
                    retry_list_id,
                    student_id,
                    attempt_no,
                    status,
                    total_score,
                    earned_score,
                    started_at,
                    submitted_at
                FROM course_exercise_retry_attempt
                WHERE retry_list_id = ?
                  AND student_id = ?
                  AND status = ?
                ORDER BY attempt_no DESC
                LIMIT 1
                FOR UPDATE
                """,
                (rs, rowNum) -> mapAttempt(rs),
                retryListId,
                studentId,
                status
        );
    }

    private AttemptHeader requireAttempt(
            Long attemptId,
            Long studentId) {

        List<AttemptHeader> rows = jdbc.query(
                """
                SELECT
                    id,
                    retry_list_id,
                    student_id,
                    attempt_no,
                    status,
                    total_score,
                    earned_score,
                    started_at,
                    submitted_at
                FROM course_exercise_retry_attempt
                WHERE id = ?
                  AND student_id = ?
                """,
                (rs, rowNum) -> mapAttempt(rs),
                attemptId,
                studentId
        );

        if (rows.isEmpty()) {
            throw new BusinessException(404, "重练记录不存在或无权查看");
        }

        return rows.get(0);
    }

    private AttemptHeader requireAttemptForUpdate(
            Long attemptId,
            Long studentId) {

        List<AttemptHeader> rows = jdbc.query(
                """
                SELECT
                    id,
                    retry_list_id,
                    student_id,
                    attempt_no,
                    status,
                    total_score,
                    earned_score,
                    started_at,
                    submitted_at
                FROM course_exercise_retry_attempt
                WHERE id = ?
                  AND student_id = ?
                FOR UPDATE
                """,
                (rs, rowNum) -> mapAttempt(rs),
                attemptId,
                studentId
        );

        if (rows.isEmpty()) {
            throw new BusinessException(404, "重练记录不存在或无权访问");
        }

        return rows.get(0);
    }

    private StudentRetryAttemptVO getAttempt(
            Long attemptId,
            Long studentId) {

        return toAttemptVO(requireAttempt(attemptId, studentId));
    }

    private AttemptHeader mapAttempt(
            java.sql.ResultSet rs) throws java.sql.SQLException {

        return new AttemptHeader(
                rs.getLong("id"),
                rs.getLong("retry_list_id"),
                rs.getLong("student_id"),
                rs.getInt("attempt_no"),
                rs.getString("status"),
                rs.getBigDecimal("total_score"),
                rs.getBigDecimal("earned_score"),
                toLocalDateTime(rs.getTimestamp("started_at")),
                toLocalDateTime(rs.getTimestamp("submitted_at"))
        );
    }

    private StudentRetryAttemptVO toAttemptVO(
            AttemptHeader attempt) {

        return new StudentRetryAttemptVO(
                attempt.id().toString(),
                attempt.retryListId().toString(),
                attempt.attemptNo(),
                attempt.status(),
                attempt.totalScore(),
                attempt.earnedScore(),
                attempt.startedAt(),
                attempt.submittedAt()
        );
    }

    private void markRetryListCompleted(
            Long retryListId,
            Long studentId) {

        jdbc.update(
                """
                UPDATE course_exercise_retry_list
                SET status = 'COMPLETED',
                    updated_at = NOW()
                WHERE id = ?
                  AND student_id = ?
                """,
                retryListId,
                studentId
        );
    }

    // =====================================================
    // 答案判定
    // =====================================================

    private boolean isCorrect(
            String type,
            String studentAnswer,
            String correctAnswer) {

        if (studentAnswer == null || studentAnswer.isBlank()
                || correctAnswer == null || correctAnswer.isBlank()) {
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

    // =====================================================
    // JSON 与时间
    // =====================================================

    private List<StudentRetryDetailVO.Option> parseOptions(
            String optionsJson) {

        if (optionsJson == null || optionsJson.isBlank()) {
            return List.of();
        }

        try {
            JsonNode root = objectMapper.readTree(optionsJson);

            if (root == null || !root.isArray()) {
                return List.of();
            }

            List<StudentRetryDetailVO.Option> options =
                    new ArrayList<>();

            for (JsonNode node : root) {
                options.add(
                        new StudentRetryDetailVO.Option(
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

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null
                ? null
                : timestamp.toLocalDateTime();
    }

    // =====================================================
    // 内部数据结构
    // =====================================================

    private record RetryListHeader(
            Long id,
            Long studentId,
            String title,
            Integer itemCount,
            String status
    ) {}

    private record AttemptHeader(
            Long id,
            Long retryListId,
            Long studentId,
            Integer attemptNo,
            String status,
            BigDecimal totalScore,
            BigDecimal earnedScore,
            LocalDateTime startedAt,
            LocalDateTime submittedAt
    ) {}

    private record RetryItemData(
            Long retryItemId,
            Long sourceAnswerId,
            Long questionId,
            Long courseId,
            String courseName,
            String exerciseTitle,
            String knowledgePointTitle,
            String questionType,
            String stem,
            List<StudentRetryDetailVO.Option> options,
            String correctAnswer,
            String explanation,
            BigDecimal score,
            Integer sortOrder
    ) {}
}
