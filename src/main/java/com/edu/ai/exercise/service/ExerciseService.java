package com.edu.ai.exercise.service;

import com.edu.ai.common.BusinessException;
import com.edu.ai.course.service.CoursePermissionService;
import com.edu.ai.exercise.dto.*;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class ExerciseService {

    private static final Set<String> QUESTION_TYPES = Set.of(
            "SINGLE_CHOICE",
            "MULTIPLE_CHOICE",
            "TRUE_FALSE",
            "SHORT_ANSWER"
    );

    private static final Pattern ANSWER_LABELS =
            Pattern.compile("^[A-Za-z0-9](\\s*[,，;； ]\\s*[A-Za-z0-9])*$");

    private final JdbcTemplate jdbc;
    private final CoursePermissionService permissions;
    private final ObjectMapper objectMapper;

    public ExerciseService(
            JdbcTemplate jdbc,
            CoursePermissionService permissions,
            ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.permissions = permissions;
        this.objectMapper = objectMapper;
    }

    /** 教师或管理员查询可管理课程的知识点，供题目绑定使用。 */
    public List<ExerciseKnowledgePointVO> listKnowledgePoints(
            Long courseId, Long userId) {
        permissions.requireManage(courseId, userId);
        return jdbc.query(
                """
                SELECT id, title, difficulty
                FROM course_knowledge_point
                WHERE course_id = ?
                ORDER BY sort_order, id
                """,
                (rs, rowNum) -> new ExerciseKnowledgePointVO(
                        rs.getString("id"),
                        rs.getString("title"),
                        rs.getInt("difficulty")
                ),
                courseId
        );
    }

    /** 创建练习草稿；题目由教师在页面逐道填写，不涉及文件上传。 */
    @Transactional(rollbackFor = Exception.class)
    public ExerciseVO create(
            Long courseId,
            Long userId,
            ExerciseSaveRequest request) {
        permissions.requireManage(courseId, userId);
        validateRequest(courseId, request);

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    """
                    INSERT INTO course_exercise
                        (course_id, title, description, difficulty,
                         estimated_minutes, status, created_by,
                         created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, 'DRAFT', ?, NOW(), NOW())
                    """,
                    Statement.RETURN_GENERATED_KEYS
            );
            ps.setLong(1, courseId);
            ps.setString(2, request.title().trim());
            ps.setString(3, trimToNull(request.description()));
            ps.setInt(4, request.difficulty());
            ps.setInt(5, request.estimatedMinutes());
            ps.setLong(6, userId);
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("创建练习失败，未获取到练习ID");
        }
        Long exerciseId = key.longValue();
        replaceQuestions(exerciseId, courseId, request.questions());
        return getManaged(exerciseId, userId);
    }

    /** 只允许修改草稿。 */
    @Transactional(rollbackFor = Exception.class)
    public ExerciseVO update(
            Long exerciseId,
            Long userId,
            ExerciseSaveRequest request) {
        ExerciseHeader header = requireHeader(exerciseId);
        permissions.requireManage(header.courseId(), userId);
        if (!"DRAFT".equals(header.status())) {
            throw new BusinessException(409, "只有草稿练习可以修改");
        }
        validateRequest(header.courseId(), request);

        jdbc.update(
                """
                UPDATE course_exercise
                SET title = ?, description = ?, difficulty = ?,
                    estimated_minutes = ?, updated_at = NOW()
                WHERE id = ? AND status = 'DRAFT'
                """,
                request.title().trim(),
                trimToNull(request.description()),
                request.difficulty(),
                request.estimatedMinutes(),
                exerciseId
        );
        replaceQuestions(exerciseId, header.courseId(), request.questions());
        return getManaged(exerciseId, userId);
    }

    /** 发布草稿，使课程学生可见。 */
    @Transactional(rollbackFor = Exception.class)
    public ExerciseVO publish(Long exerciseId, Long userId) {
        ExerciseHeader header = requireHeader(exerciseId);
        permissions.requireManage(header.courseId(), userId);
        if ("PUBLISHED".equals(header.status())) {
            return getManaged(exerciseId, userId);
        }
        if (!"DRAFT".equals(header.status())) {
            throw new BusinessException(409, "当前练习状态不能发布");
        }
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM course_exercise_question WHERE exercise_id = ?",
                Long.class, exerciseId
        );
        if (count == null || count == 0) {
            throw new BusinessException(400, "练习至少需要一道题目才能发布");
        }
        jdbc.update(
                """
                UPDATE course_exercise
                SET status = 'PUBLISHED', published_at = NOW(), updated_at = NOW()
                WHERE id = ? AND status = 'DRAFT'
                """,
                exerciseId
        );
        return getManaged(exerciseId, userId);
    }

    /** 关闭已发布练习，不删除历史数据。 */
    @Transactional(rollbackFor = Exception.class)
    public ExerciseVO close(Long exerciseId, Long userId) {
        ExerciseHeader header = requireHeader(exerciseId);
        permissions.requireManage(header.courseId(), userId);
        if (!"PUBLISHED".equals(header.status())) {
            throw new BusinessException(409, "只有已发布练习可以关闭");
        }
        jdbc.update(
                "UPDATE course_exercise SET status = 'CLOSED', updated_at = NOW() WHERE id = ?",
                exerciseId
        );
        return getManaged(exerciseId, userId);
    }

    /** 仅草稿允许删除。 */
    @Transactional(rollbackFor = Exception.class)
    public void deleteDraft(Long exerciseId, Long userId) {
        ExerciseHeader header = requireHeader(exerciseId);
        permissions.requireManage(header.courseId(), userId);
        if (!"DRAFT".equals(header.status())) {
            throw new BusinessException(409, "只有草稿练习可以删除");
        }
        jdbc.update("DELETE FROM course_exercise WHERE id = ?", exerciseId);
    }

    /** 教师/管理员查看练习列表。 */
    public List<ExerciseVO> listManaged(
            Long userId, String role, Long courseId) {
        if (courseId != null) {
            permissions.requireManage(courseId, userId);
        }
        StringBuilder sql = new StringBuilder("""
                SELECT e.id, e.course_id, c.name AS course_name,
                       e.title, e.description, e.difficulty,
                       e.estimated_minutes, e.status, e.created_by,
                       e.published_at, e.created_at, e.updated_at,
                       (SELECT COUNT(*) FROM course_exercise_question q
                        WHERE q.exercise_id = e.id) AS question_count
                FROM course_exercise e
                JOIN course c ON c.id = e.course_id
                WHERE (? = 'ADMIN' OR c.user_id = ?)
                """);
        List<Object> args = new ArrayList<>(List.of(role, userId));
        if (courseId != null) {
            sql.append(" AND e.course_id = ?");
            args.add(courseId);
        }
        sql.append(" ORDER BY e.updated_at DESC, e.id DESC");
        return jdbc.query(
                sql.toString(),
                (rs, rowNum) -> mapExercise(rs),
                args.toArray()
        );
    }

    /** 课程学生只查看已发布练习；教师可查看自己课程的所有状态。 */
    public ExerciseVO getDetail(Long exerciseId, Long userId) {
        ExerciseHeader header = requireHeader(exerciseId);
        permissions.requireRead(header.courseId(), userId);
        boolean manager = permissions.canManage(header.courseId(), userId);
        if (!manager && !"PUBLISHED".equals(header.status())) {
            throw new BusinessException(404, "练习不存在或尚未发布");
        }
        return getById(exerciseId, manager);
    }

    private ExerciseVO getManaged(Long exerciseId, Long userId) {
        ExerciseHeader header = requireHeader(exerciseId);
        permissions.requireManage(header.courseId(), userId);
        return getById(exerciseId, true);
    }

    private ExerciseVO getById(Long exerciseId, boolean includeAnswers) {
        List<ExerciseVO> rows = jdbc.query(
                """
                SELECT e.id, e.course_id, c.name AS course_name,
                       e.title, e.description, e.difficulty,
                       e.estimated_minutes, e.status, e.created_by,
                       e.published_at, e.created_at, e.updated_at,
                       (SELECT COUNT(*) FROM course_exercise_question q
                        WHERE q.exercise_id = e.id) AS question_count
                FROM course_exercise e
                JOIN course c ON c.id = e.course_id
                WHERE e.id = ?
                """,
                (rs, rowNum) -> mapExercise(rs),
                exerciseId
        );
        if (rows.isEmpty()) {
            throw new BusinessException(404, "练习不存在");
        }
        ExerciseVO header = rows.get(0);
        List<ExerciseQuestionVO> questions = jdbc.query(
                """
                SELECT q.id, q.knowledge_point_id, p.title AS knowledge_point_title,
                       q.question_type, q.stem, q.options_json,
                       q.correct_answer, q.explanation, q.score, q.sort_order
                FROM course_exercise_question q
                JOIN course_knowledge_point p
                  ON p.id = q.knowledge_point_id
                 AND p.course_id = ?
                WHERE q.exercise_id = ?
                ORDER BY q.sort_order, q.id
                """,
                (rs, rowNum) -> {
                    JsonNode options = null;
                    String optionsJson = rs.getString("options_json");
                    if (optionsJson != null && !optionsJson.isBlank()) {
                        try {
                            options = objectMapper.readTree(optionsJson);
                        } catch (Exception ignored) {
                            options = null;
                        }
                    }
                    return new ExerciseQuestionVO(
                            rs.getString("id"),
                            rs.getString("knowledge_point_id"),
                            rs.getString("knowledge_point_title"),
                            rs.getString("question_type"),
                            rs.getString("stem"),
                            options,
                            includeAnswers ? rs.getString("correct_answer") : null,
                            includeAnswers ? rs.getString("explanation") : null,
                            rs.getBigDecimal("score"),
                            rs.getInt("sort_order")
                    );
                },
                Long.valueOf(header.courseId()),
                exerciseId
        );
        return new ExerciseVO(
                header.id(), header.courseId(), header.courseName(),
                header.title(), header.description(), header.difficulty(),
                header.estimatedMinutes(), header.status(), header.createdBy(),
                header.publishedAt(), header.createdAt(), header.updatedAt(),
                header.questionCount(), questions
        );
    }

    private ExerciseVO mapExercise(java.sql.ResultSet rs)
            throws java.sql.SQLException {
        Timestamp published = rs.getTimestamp("published_at");
        Timestamp created = rs.getTimestamp("created_at");
        Timestamp updated = rs.getTimestamp("updated_at");
        return new ExerciseVO(
                rs.getString("id"),
                rs.getString("course_id"),
                rs.getString("course_name"),
                rs.getString("title"),
                rs.getString("description"),
                rs.getInt("difficulty"),
                rs.getInt("estimated_minutes"),
                rs.getString("status"),
                rs.getString("created_by"),
                published == null ? null : published.toLocalDateTime(),
                created == null ? null : created.toLocalDateTime(),
                updated == null ? null : updated.toLocalDateTime(),
                rs.getInt("question_count"),
                List.of()
        );
    }

    private void replaceQuestions(
            Long exerciseId,
            Long courseId,
            List<ExerciseQuestionRequest> questions) {
        jdbc.update(
                "DELETE FROM course_exercise_question WHERE exercise_id = ?",
                exerciseId
        );
        int order = 1;
        for (ExerciseQuestionRequest question : questions) {
            String optionsJson = null;
            if (question.options() != null && !question.options().isEmpty()) {
                try {
                    optionsJson = objectMapper.writeValueAsString(question.options());
                } catch (Exception e) {
                    throw new BusinessException(400, "题目选项格式不正确");
                }
            }
            jdbc.update(
                    """
                    INSERT INTO course_exercise_question
                        (exercise_id, knowledge_point_id, question_type,
                         stem, options_json, correct_answer, explanation,
                         score, sort_order, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
                    """,
                    exerciseId,
                    question.knowledgePointId(),
                    question.questionType().trim().toUpperCase(Locale.ROOT),
                    question.stem().trim(),
                    optionsJson,
                    question.correctAnswer().trim(),
                    trimToNull(question.explanation()),
                    question.score(),
                    order++
            );
        }
    }

    private void validateRequest(Long courseId, ExerciseSaveRequest request) {
        if (request == null || request.questions() == null || request.questions().isEmpty()) {
            throw new BusinessException(400, "至少添加一道题目");
        }
        for (ExerciseQuestionRequest question : request.questions()) {
            validateQuestion(courseId, question);
        }
    }

    private void validateQuestion(Long courseId, ExerciseQuestionRequest question) {
        if (question == null) {
            throw new BusinessException(400, "题目信息不能为空");
        }
        String type = question.questionType() == null
                ? "" : question.questionType().trim().toUpperCase(Locale.ROOT);
        if (!QUESTION_TYPES.contains(type)) {
            throw new BusinessException(400, "不支持的题型：" + type);
        }
        Long pointCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM course_knowledge_point WHERE id = ? AND course_id = ?",
                Long.class, question.knowledgePointId(), courseId
        );
        if (pointCount == null || pointCount == 0) {
            throw new BusinessException(400, "题目关联的知识点不属于当前课程");
        }

        List<ExerciseOptionRequest> options = question.options() == null
                ? List.of() : question.options();
        if ("SINGLE_CHOICE".equals(type) || "MULTIPLE_CHOICE".equals(type)) {
            if (options.size() < 2) {
                throw new BusinessException(400, "选择题至少需要两个选项");
            }
            Set<String> labels = new HashSet<>();
            for (ExerciseOptionRequest option : options) {
                String label = option.label().trim().toUpperCase(Locale.ROOT);
                if (!labels.add(label)) {
                    throw new BusinessException(400, "选择题选项标识不能重复");
                }
            }
            String answer = question.correctAnswer().trim().toUpperCase(Locale.ROOT)
                    .replace('，', ',').replace('；', ',').replace(';', ',').replace(' ', ',');
            if (!ANSWER_LABELS.matcher(answer).matches()) {
                throw new BusinessException(400, "参考答案请填写选项标识，例如 A 或 A,B");
            }
            Set<String> answerLabels = new HashSet<>();
            for (String label : answer.split(",")) {
                answerLabels.add(label.trim());
            }
            if (!labels.containsAll(answerLabels)) {
                throw new BusinessException(400, "参考答案包含不存在的选项");
            }
            if ("SINGLE_CHOICE".equals(type) && answerLabels.size() != 1) {
                throw new BusinessException(400, "单选题只能设置一个正确选项");
            }
        } else if ("TRUE_FALSE".equals(type)) {
            String answer = question.correctAnswer().trim().toUpperCase(Locale.ROOT);
            if (!Set.of("TRUE", "FALSE", "正确", "错误").contains(answer)) {
                throw new BusinessException(400, "判断题答案请填写 TRUE、FALSE、正确或错误");
            }
        }
    }

    private ExerciseHeader requireHeader(Long exerciseId) {
        List<ExerciseHeader> rows = jdbc.query(
                "SELECT id, course_id, status FROM course_exercise WHERE id = ?",
                (rs, rowNum) -> new ExerciseHeader(
                        rs.getLong("id"), rs.getLong("course_id"), rs.getString("status")),
                exerciseId
        );
        if (rows.isEmpty()) {
            throw new BusinessException(404, "练习不存在");
        }
        return rows.get(0);
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    private record ExerciseHeader(Long id, Long courseId, String status) {}
}
