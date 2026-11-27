package com.edu.ai.exercise.service;

import com.edu.ai.common.BusinessException;
import com.edu.ai.course.service.CoursePermissionService;
import com.edu.ai.exercise.dto.StudentMistakeDetailVO;
import com.edu.ai.exercise.dto.StudentMistakeRetryCreateRequest;
import com.edu.ai.exercise.dto.StudentMistakeRetryListVO;
import com.edu.ai.exercise.dto.StudentMistakeSummaryVO;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class StudentMistakeService {

    private final JdbcTemplate jdbc;
    private final CoursePermissionService permissions;

    public StudentMistakeService(
            JdbcTemplate jdbc,
            CoursePermissionService permissions) {
        this.jdbc = jdbc;
        this.permissions = permissions;
    }

    /**
     * 查询学生错题，并按照知识点归集。
     *
     * 错题判定：
     * 1. pending_review = 0
     * 2. is_correct = 0
     *
     * 未完成教师批改的简答题不进入错题本。
     */
    public StudentMistakeSummaryVO listMistakes(
            Long studentId,
            Long courseId) {

        if (courseId != null) {
            permissions.requireActiveRead(courseId, studentId);
        }

        StringBuilder sql = new StringBuilder("""
                SELECT
                    aa.id AS answer_id,
                    a.id AS attempt_id,
                    e.id AS exercise_id,
                    e.title AS exercise_title,
                    c.id AS course_id,
                    c.name AS course_name,

                    kp.id AS knowledge_point_id,
                    kp.title AS knowledge_point_title,

                    q.id AS question_id,
                    q.question_type,
                    q.stem,

                    aa.student_answer,
                    q.correct_answer,
                    q.explanation,

                    CASE
                        WHEN TRIM(COALESCE(aa.student_answer, '')) = ''
                            THEN 'UNANSWERED'

                        WHEN q.question_type = 'SHORT_ANSWER'
                            THEN 'SHORT_ANSWER_INCOMPLETE'

                        ELSE 'OBJECTIVE_WRONG'
                    END AS mistake_type,

                    aa.teacher_feedback,
                    q.score AS max_score,
                    aa.score_earned,
                    aa.created_at AS answered_at

                FROM course_exercise_attempt_answer aa

                JOIN course_exercise_attempt a
                    ON a.id = aa.attempt_id

                JOIN course_exercise_question q
                    ON q.id = aa.question_id

                JOIN course_exercise e
                    ON e.id = a.exercise_id

                JOIN course c
                    ON c.id = a.course_id

                JOIN course_student cs
                    ON cs.course_id = c.id
                   AND cs.student_id = a.student_id

                LEFT JOIN course_knowledge_point kp
                    ON kp.id = q.knowledge_point_id
                   AND kp.course_id = c.id

                WHERE a.student_id = ?
                  AND c.status = 1
                  AND aa.pending_review = 0
                  AND aa.is_correct = 0
                """);

        List<Object> args = new ArrayList<>();
        args.add(studentId);

        if (courseId != null) {
            sql.append(" AND c.id = ? ");
            args.add(courseId);
        }

        sql.append("""
                ORDER BY
                    kp.sort_order ASC,
                    kp.id ASC,
                    aa.created_at DESC,
                    aa.id DESC
                """);

        List<StudentMistakeSummaryVO.MistakeItem> mistakes =
                jdbc.query(
                        sql.toString(),
                        (rs, rowNum) ->
                                new StudentMistakeSummaryVO.MistakeItem(
                                        rs.getString("answer_id"),
                                        rs.getString("attempt_id"),
                                        rs.getString("exercise_id"),
                                        rs.getString("exercise_title"),
                                        rs.getString("course_id"),
                                        rs.getString("course_name"),
                                        rs.getString("question_id"),
                                        rs.getString("question_type"),
                                        rs.getString("stem"),
                                        rs.getString("student_answer"),
                                        rs.getString("correct_answer"),
                                        rs.getString("explanation"),
                                        rs.getString("mistake_type"),
                                        rs.getString("teacher_feedback"),
                                        rs.getBigDecimal("max_score"),
                                        rs.getBigDecimal("score_earned"),
                                        toLocalDateTime(
                                                rs.getTimestamp("answered_at")
                                        )
                                ),
                        args.toArray()
                );

        // 使用 LinkedHashMap 保留知识点的查询顺序
        Map<String, List<StudentMistakeSummaryVO.MistakeItem>> grouped =
                new LinkedHashMap<>();

        Map<String, String> titles = new LinkedHashMap<>();

        for (StudentMistakeSummaryVO.MistakeItem item : mistakes) {

            String key = item.knowledgePointId() == null
                    ? "0"
                    : item.knowledgePointId();

            grouped.computeIfAbsent(
                    key,
                    ignored -> new ArrayList<>()
            ).add(item);

            titles.putIfAbsent(
                    key,
                    item.knowledgePointTitle() == null
                            ? "未关联知识点"
                            : item.knowledgePointTitle()
            );
        }

        List<StudentMistakeSummaryVO.KnowledgePointGroup> groups =
                new ArrayList<>();

        for (Map.Entry<String,
                List<StudentMistakeSummaryVO.MistakeItem>> entry
                : grouped.entrySet()) {

            groups.add(
                    new StudentMistakeSummaryVO.KnowledgePointGroup(
                            "0".equals(entry.getKey())
                                    ? null
                                    : entry.getKey(),
                            titles.get(entry.getKey()),
                            entry.getValue().size(),
                            entry.getValue()
                    )
            );
        }

        return new StudentMistakeSummaryVO(
                mistakes.size(),
                groups.size(),
                groups
        );
    }

    /**
     * 查询某道错题的完整详情。
     */
    public StudentMistakeDetailVO detail(
            Long answerId,
            Long studentId) {

        List<StudentMistakeDetailVO> rows = jdbc.query(
                """
                SELECT
                    aa.id AS answer_id,
                    a.id AS attempt_id,
                    e.id AS exercise_id,
                    e.title AS exercise_title,
                    c.id AS course_id,
                    c.name AS course_name,

                    kp.id AS knowledge_point_id,
                    kp.title AS knowledge_point_title,

                    q.id AS question_id,
                    q.question_type,
                    q.stem,

                    aa.student_answer,
                    q.correct_answer,
                    q.explanation,

                    CASE
                        WHEN TRIM(COALESCE(aa.student_answer, '')) = ''
                            THEN 'UNANSWERED'
                        WHEN q.question_type = 'SHORT_ANSWER'
                            THEN 'SHORT_ANSWER_INCOMPLETE'
                        ELSE 'OBJECTIVE_WRONG'
                    END AS mistake_type,

                    aa.teacher_feedback,
                    q.score AS max_score,
                    aa.score_earned,
                    aa.created_at AS answered_at

                FROM course_exercise_attempt_answer aa

                JOIN course_exercise_attempt a
                    ON a.id = aa.attempt_id

                JOIN course_exercise_question q
                    ON q.id = aa.question_id

                JOIN course_exercise e
                    ON e.id = a.exercise_id

                JOIN course c
                    ON c.id = a.course_id

                JOIN course_student cs
                    ON cs.course_id = c.id
                   AND cs.student_id = a.student_id

                LEFT JOIN course_knowledge_point kp
                    ON kp.id = q.knowledge_point_id
                   AND kp.course_id = c.id

                WHERE aa.id = ?
                  AND a.student_id = ?
                  AND c.status = 1
                  AND aa.pending_review = 0
                  AND aa.is_correct = 0
                """,
                (rs, rowNum) -> new StudentMistakeDetailVO(
                        rs.getString("answer_id"),
                        rs.getString("attempt_id"),
                        rs.getString("exercise_id"),
                        rs.getString("exercise_title"),
                        rs.getString("course_id"),
                        rs.getString("course_name"),
                        rs.getString("knowledge_point_id"),
                        rs.getString("knowledge_point_title"),
                        rs.getString("question_id"),
                        rs.getString("question_type"),
                        rs.getString("stem"),
                        rs.getString("student_answer"),
                        rs.getString("correct_answer"),
                        rs.getString("explanation"),
                        rs.getString("mistake_type"),
                        rs.getString("teacher_feedback"),
                        rs.getBigDecimal("max_score"),
                        rs.getBigDecimal("score_earned"),
                        toLocalDateTime(
                                rs.getTimestamp("answered_at")
                        )
                ),
                answerId,
                studentId
        );

        if (rows.isEmpty()) {
            throw new BusinessException(
                    404,
                    "错题不存在或无权查看"
            );
        }

        StudentMistakeDetailVO mistake = rows.get(0);

        permissions.requireActiveRead(
                Long.valueOf(mistake.courseId()),
                studentId
        );

        return mistake;
    }

    /**
     * 根据学生选中的错题生成持久化重练清单。
     *
     * 只允许选择：
     * 1. 当前登录学生自己的答题记录。
     * 2. 已经完成判分的错题。
     * 3. 仍然属于学生已加入正常课程的题目。
     */
    @Transactional(rollbackFor = Exception.class)
    public StudentMistakeRetryListVO createRetryList(
            Long studentId,
            StudentMistakeRetryCreateRequest request) {

        if (request == null
                || request.answerIds() == null
                || request.answerIds().isEmpty()) {
            throw new BusinessException(400, "请选择错题");
        }

        Set<Long> uniqueIds = new LinkedHashSet<>(request.answerIds());

        if (uniqueIds.size() != request.answerIds().size()) {
            throw new BusinessException(400, "重练清单不能包含重复错题");
        }

        List<Long> answerIds = new ArrayList<>(uniqueIds);

        String placeholders = String.join(
                ",",
                java.util.Collections.nCopies(answerIds.size(), "?")
        );

        List<Object> args = new ArrayList<>(answerIds);
        args.add(studentId);

        String sql = """
                SELECT
                    aa.id AS answer_id,
                    aa.question_id
                FROM course_exercise_attempt_answer aa
                JOIN course_exercise_attempt a
                    ON a.id = aa.attempt_id
                JOIN course c
                    ON c.id = a.course_id
                JOIN course_student cs
                    ON cs.course_id = c.id
                   AND cs.student_id = a.student_id
                WHERE aa.id IN (%s)
                  AND a.student_id = ?
                  AND c.status = 1
                  AND aa.pending_review = 0
                  AND aa.is_correct = 0
                """.formatted(placeholders);

        record ValidMistake(Long answerId, Long questionId) {}

        List<ValidMistake> validMistakes = jdbc.query(
                sql,
                (rs, rowNum) -> new ValidMistake(
                        rs.getLong("answer_id"),
                        rs.getLong("question_id")
                ),
                args.toArray()
        );

        if (validMistakes.size() != answerIds.size()) {
            throw new BusinessException(
                    400,
                    "部分错题不存在、尚未批改或不属于当前学生，无法生成清单"
            );
        }

        String title = request.title() == null
                || request.title().isBlank()
                ? "我的错题重练"
                : request.title().trim();

        if (title.length() > 100) {
            throw new BusinessException(400, "清单名称不能超过100字");
        }

        KeyHolder keyHolder = new GeneratedKeyHolder();

        int inserted = jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    """
                    INSERT INTO course_exercise_retry_list
                        (student_id, title, item_count, status,
                         created_at, updated_at)
                    VALUES (?, ?, ?, 'PENDING', NOW(), NOW())
                    """,
                    Statement.RETURN_GENERATED_KEYS
            );

            ps.setLong(1, studentId);
            ps.setString(2, title);
            ps.setInt(3, validMistakes.size());

            return ps;
        }, keyHolder);

        Number generatedId = keyHolder.getKey();

        if (inserted != 1 || generatedId == null) {
            throw new IllegalStateException("创建重练清单失败");
        }

        Long retryListId = generatedId.longValue();

        for (int i = 0; i < validMistakes.size(); i++) {

            ValidMistake mistake = validMistakes.get(i);

            jdbc.update(
                    """
                    INSERT INTO course_exercise_retry_item
                        (retry_list_id, source_answer_id,
                         question_id, sort_order, created_at)
                    VALUES (?, ?, ?, ?, NOW())
                    """,
                    retryListId,
                    mistake.answerId(),
                    mistake.questionId(),
                    i + 1
            );
        }

        return new StudentMistakeRetryListVO(
                retryListId.toString(),
                title,
                validMistakes.size(),
                LocalDateTime.now()
        );
    }

    private java.time.LocalDateTime toLocalDateTime(
            Timestamp timestamp) {
        return timestamp == null
                ? null
                : timestamp.toLocalDateTime();
    }
}
