package com.edu.ai.learning.service;

import com.edu.ai.common.BusinessException;
import com.edu.ai.course.service.CoursePermissionService;
import com.edu.ai.learning.dto.LearningReportCommentRequest;
import com.edu.ai.learning.dto.StudentLearningReportDetailVO;
import com.edu.ai.learning.dto.StudentLearningReportDetailVO.CommentVO;
import com.edu.ai.learning.dto.StudentLearningReportDetailVO.WeakPointSnapshotVO;
import com.edu.ai.learning.dto.StudentLearningReportRequest;
import com.edu.ai.learning.dto.StudentLearningReportVO;
import com.edu.ai.task.dto.StudentWeakPointDiagnosticVO;
import com.edu.ai.task.service.StudentWeakPointDiagnosticService;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class StudentLearningReportService {

    private static final int MAX_PERIOD_DAYS = 366;

    private static final ZoneId APP_ZONE =
            ZoneId.of("Asia/Shanghai");

    private final JdbcTemplate jdbc;

    private final CoursePermissionService permissions;

    private final StudentWeakPointDiagnosticService diagnosticService;

    public StudentLearningReportService(
            JdbcTemplate jdbc,
            CoursePermissionService permissions,
            StudentWeakPointDiagnosticService diagnosticService) {

        this.jdbc = jdbc;
        this.permissions = permissions;
        this.diagnosticService = diagnosticService;
    }

    /**
     * 学生生成阶段学习报告。
     *
     * 报告统计值和薄弱知识点均在生成时保存快照。
     */
    @Transactional(rollbackFor = Exception.class)
    public StudentLearningReportDetailVO generate(
            Long courseId,
            Long studentId,
            StudentLearningReportRequest request) {

        permissions.requireActiveRead(courseId, studentId);

        validatePeriod(request);

        LocalDateTime startAt =
                request.startDate().atStartOfDay();

        LocalDateTime endAt =
                request.endDate().plusDays(1).atStartOfDay();

        ExerciseStats exercise = queryExerciseStats(
                courseId,
                studentId,
                startAt,
                endAt
        );

        long questionCount = queryQuestionCount(
                courseId,
                studentId,
                startAt,
                endAt
        );

        TaskStats tasks = queryTaskStats(
                courseId,
                studentId,
                startAt,
                endAt
        );

        // 复用 F6-02 已有诊断服务。
        // 保存生成报告时的当前诊断快照。
        List<StudentWeakPointDiagnosticVO> weakPoints =
                diagnosticService.diagnose(courseId, studentId)
                        .stream()
                        .filter(point -> point.diagnosisLevel() < 2)
                        .toList();

        String summary = buildSummary(
                request.startDate(),
                request.endDate(),
                exercise,
                questionCount,
                tasks,
                weakPoints.size()
        );

        String suggestions = buildSuggestions(
                exercise,
                tasks,
                weakPoints
        );

        String title = "阶段学习报告 "
                + request.startDate()
                + " 至 "
                + request.endDate();

        KeyHolder keyHolder = new GeneratedKeyHolder();

        String insertSql = """
                INSERT INTO student_learning_report (
                    student_id,
                    course_id,
                    title,
                    period_start,
                    period_end,
                    exercise_count,
                    objective_answer_count,
                    correct_objective_count,
                    exercise_accuracy_rate,
                    question_count,
                    completed_task_count,
                    total_task_count,
                    weak_point_count,
                    report_summary,
                    improvement_suggestions
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        jdbc.update(connection -> {

            PreparedStatement ps = connection.prepareStatement(
                    insertSql,
                    Statement.RETURN_GENERATED_KEYS
            );

            ps.setLong(1, studentId);
            ps.setLong(2, courseId);
            ps.setString(3, title);

            ps.setDate(
                    4,
                    java.sql.Date.valueOf(request.startDate())
            );

            ps.setDate(
                    5,
                    java.sql.Date.valueOf(request.endDate())
            );

            ps.setLong(6, exercise.exerciseCount());

            ps.setLong(7, exercise.objectiveAnswerCount());

            ps.setLong(8, exercise.correctObjectiveCount());

            if (exercise.accuracyRate() == null) {
                ps.setNull(9, java.sql.Types.DECIMAL);
            } else {
                ps.setBigDecimal(9, exercise.accuracyRate());
            }

            ps.setLong(10, questionCount);

            ps.setLong(11, tasks.completedTaskCount());

            ps.setLong(12, tasks.totalTaskCount());

            ps.setInt(13, weakPoints.size());

            ps.setString(14, summary);

            ps.setString(15, suggestions);

            return ps;

        }, keyHolder);

        Number generatedId = keyHolder.getKey();

        if (generatedId == null) {
            throw new BusinessException(500, "学习报告创建失败");
        }

        Long reportId = generatedId.longValue();

        saveWeakPointSnapshots(reportId, weakPoints);

        return getForStudent(
                courseId,
                reportId,
                studentId
        );
    }

    /**
     * 学生查看自己在指定课程中的报告历史。
     */
    public List<StudentLearningReportVO> listForStudent(
            Long courseId,
            Long studentId) {

        permissions.requireActiveRead(courseId, studentId);

        return queryReportList(
                "WHERE r.course_id = ? AND r.student_id = ?",
                courseId,
                studentId
        );
    }

    /**
     * 学生查看自己的报告详情。
     */
    public StudentLearningReportDetailVO getForStudent(
            Long courseId,
            Long reportId,
            Long studentId) {

        permissions.requireActiveRead(courseId, studentId);

        ReportRow report = findReport(
                "WHERE r.id = ? AND r.course_id = ? AND r.student_id = ?",
                reportId,
                courseId,
                studentId
        );

        return toDetail(report);
    }

    /**
     * 教师查看自己管理课程的学生报告列表。
     */
    public List<StudentLearningReportVO> listForTeacher(
            Long courseId,
            Long teacherId) {

        permissions.requireManage(courseId, teacherId);

        return queryReportList(
                "WHERE r.course_id = ?",
                courseId
        );
    }

    /**
     * 教师查看自己管理课程中的一份报告。
     */
    public StudentLearningReportDetailVO getForTeacher(
            Long courseId,
            Long reportId,
            Long teacherId) {

        permissions.requireManage(courseId, teacherId);

        ReportRow report = findReport(
                "WHERE r.id = ? AND r.course_id = ?",
                reportId,
                courseId
        );

        return toDetail(report);
    }

    /**
     * 教师为报告追加文字批注，保留每次批注历史。
     */
    @Transactional(rollbackFor = Exception.class)
    public CommentVO addTeacherComment(
            Long courseId,
            Long reportId,
            Long teacherId,
            LearningReportCommentRequest request) {

        permissions.requireManage(courseId, teacherId);

        if (request == null
                || request.content() == null
                || request.content().isBlank()) {

            throw new BusinessException(400, "批注内容不能为空");
        }

        String content = request.content().trim();

        if (content.length() > 2000) {
            throw new BusinessException(
                    400,
                    "批注内容不能超过2000字"
            );
        }

        // 确认报告属于当前教师管理的课程
        findReport(
                "WHERE r.id = ? AND r.course_id = ?",
                reportId,
                courseId
        );

        KeyHolder keyHolder = new GeneratedKeyHolder();

        int affectedRows = jdbc.update(connection -> {

            PreparedStatement ps = connection.prepareStatement(
                    """
                    INSERT INTO student_learning_report_comment (
                        report_id,
                        teacher_id,
                        comment_content
                    ) VALUES (?, ?, ?)
                    """,
                    Statement.RETURN_GENERATED_KEYS
            );

            ps.setLong(1, reportId);
            ps.setLong(2, teacherId);
            ps.setString(3, content);

            return ps;

        }, keyHolder);

        Number generatedId = keyHolder.getKey();

        if (affectedRows != 1 || generatedId == null) {
            throw new BusinessException(500, "批注保存失败");
        }

        Long commentId = generatedId.longValue();

        // 精确查询本次新增的批注
        List<CommentVO> comments = jdbc.query(
                """
                SELECT
                    CAST(c.id AS CHAR) AS id,

                    CAST(c.teacher_id AS CHAR) AS teacher_id,

                    COALESCE(
                        NULLIF(u.nickname, ''),
                        u.username
                    ) AS teacher_name,

                    c.comment_content,

                    c.created_at

                FROM student_learning_report_comment c

                JOIN sys_user u
                  ON u.id = c.teacher_id

                WHERE c.id = ?
                  AND c.report_id = ?
                  AND c.teacher_id = ?
                """,
                (rs, rowNum) -> new CommentVO(
                        rs.getString("id"),
                        rs.getString("teacher_id"),
                        rs.getString("teacher_name"),
                        rs.getString("comment_content"),
                        toLocalDateTime(
                                rs.getTimestamp("created_at")
                        )
                ),
                commentId,
                reportId,
                teacherId
        );

        if (comments.isEmpty()) {
            throw new BusinessException(500, "批注保存失败");
        }

        return comments.get(0);
    }

    /**
     * 校验报告统计时间范围。
     */
    private void validatePeriod(
            StudentLearningReportRequest request) {

        if (request == null
                || request.startDate() == null
                || request.endDate() == null) {

            throw new BusinessException(400, "请选择报告统计时间范围");
        }

        if (request.endDate().isBefore(request.startDate())) {
            throw new BusinessException(400, "结束日期不能早于开始日期");
        }

        if (request.endDate().isAfter(LocalDate.now(APP_ZONE))) {
            throw new BusinessException(400, "结束日期不能晚于今天");
        }

        long days = ChronoUnit.DAYS.between(
                request.startDate(),
                request.endDate()
        ) + 1;

        if (days > MAX_PERIOD_DAYS) {
            throw new BusinessException(
                    400,
                    "单次报告统计范围不能超过366天"
            );
        }
    }

    /**
     * 查询报告周期内的练习统计。
     *
     * 只统计已提交的练习。
     * 正确率只统计已判分的客观题。
     */
    private ExerciseStats queryExerciseStats(
            Long courseId,
            Long studentId,
            LocalDateTime startAt,
            LocalDateTime endAt) {

        return jdbc.queryForObject(
                """
                SELECT
                    COUNT(DISTINCT a.id) AS exercise_count,

                    COUNT(q.id) AS objective_answer_count,

                    COALESCE(
                        SUM(
                            CASE
                                WHEN q.id IS NOT NULL
                                 AND aa.is_correct = 1
                                THEN 1
                                ELSE 0
                            END
                        ),
                        0
                    ) AS correct_objective_count

                FROM course_exercise_attempt a

                LEFT JOIN course_exercise_attempt_answer aa
                  ON aa.attempt_id = a.id
                 AND aa.is_correct IS NOT NULL

                LEFT JOIN course_exercise_question q
                  ON q.id = aa.question_id
                 AND q.question_type <> 'SHORT_ANSWER'

                WHERE a.student_id = ?
                  AND a.course_id = ?
                  AND a.status IN ('GRADED', 'PENDING_REVIEW')
                  AND a.submitted_at >= ?
                  AND a.submitted_at < ?
                """,
                (rs, rowNum) -> {

                    long exerciseCount =
                            rs.getLong("exercise_count");

                    long objectiveCount =
                            rs.getLong("objective_answer_count");

                    long correctCount =
                            rs.getLong("correct_objective_count");

                    BigDecimal rate = objectiveCount == 0
                            ? null
                            : BigDecimal.valueOf(correctCount)
                                    .multiply(BigDecimal.valueOf(100))
                                    .divide(
                                            BigDecimal.valueOf(objectiveCount),
                                            2,
                                            RoundingMode.HALF_UP
                                    );

                    return new ExerciseStats(
                            exerciseCount,
                            objectiveCount,
                            correctCount,
                            rate
                    );
                },
                studentId,
                courseId,
                Timestamp.valueOf(startAt),
                Timestamp.valueOf(endAt)
        );
    }

    /**
     * 统计报告周期内学生的普通课程提问次数。
     *
     * 排除错题 AI 专属讲解会话。
     */
    private long queryQuestionCount(
            Long courseId,
            Long studentId,
            LocalDateTime startAt,
            LocalDateTime endAt) {

        Long count = jdbc.queryForObject(
                """
                SELECT COUNT(DISTINCT m.id)

                FROM chat_message m

                JOIN chat_session s
                  ON s.id = m.session_id

                WHERE s.user_id = ?
                  AND s.course_id = ?
                  AND m.role = 'user'
                  AND m.create_time >= ?
                  AND m.create_time < ?

                  AND NOT EXISTS (
                      SELECT 1
                      FROM course_exercise_explain_session ees
                      WHERE ees.chat_session_id = s.id
                  )
                """,
                Long.class,
                studentId,
                courseId,
                Timestamp.valueOf(startAt),
                Timestamp.valueOf(endAt)
        );

        return count == null ? 0L : count;
    }

    /**
     * 统计报告周期内创建的有效课程任务及其当前完成状态。
     *
     * task_student 只保存当前完成状态，
     * 不具备历史状态流水，因此不伪造历史完成时间。
     */
    private TaskStats queryTaskStats(
            Long courseId,
            Long studentId,
            LocalDateTime startAt,
            LocalDateTime endAt) {

        return jdbc.queryForObject(
                """
                SELECT
                    COUNT(DISTINCT t.id) AS total_task_count,

                    COUNT(
                        DISTINCT CASE
                            WHEN ts.completed = 1 THEN t.id
                        END
                    ) AS completed_task_count

                FROM learning_task t

                LEFT JOIN task_student ts
                  ON ts.task_id = t.id
                 AND ts.student_id = ?

                WHERE t.course_id = ?
                  AND t.status = 1
                  AND t.create_time >= ?
                  AND t.create_time < ?
                """,
                (rs, rowNum) -> new TaskStats(
                        rs.getLong("completed_task_count"),
                        rs.getLong("total_task_count")
                ),
                studentId,
                courseId,
                Timestamp.valueOf(startAt),
                Timestamp.valueOf(endAt)
        );
    }

    /**
     * 保存报告生成时的薄弱知识点快照。
     */
    private void saveWeakPointSnapshots(
            Long reportId,
            List<StudentWeakPointDiagnosticVO> weakPoints) {

        if (weakPoints.isEmpty()) {
            return;
        }

        jdbc.batchUpdate(
                """
                INSERT INTO student_learning_report_weak_point (
                    report_id,
                    point_id,
                    point_title,
                    effective_question_count,
                    question_session_count,
                    question_inferred_level,
                    practice_question_count,
                    mastered_question_count,
                    practice_mastery_rate,
                    diagnosis_level,
                    diagnosis_label,
                    diagnosis_reason,
                    suggestion
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                weakPoints,
                weakPoints.size(),
                (ps, point) -> {

                    ps.setLong(1, reportId);

                    ps.setLong(
                            2,
                            Long.parseLong(point.pointId())
                    );

                    ps.setString(3, point.title());

                    ps.setInt(4, point.effectiveQuestionCount());

                    ps.setInt(5, point.questionSessionCount());

                    ps.setInt(6, point.questionInferredLevel());

                    ps.setInt(7, point.practiceQuestionCount());

                    ps.setInt(8, point.masteredQuestionCount());

                    if (point.practiceMasteryRate() == null) {
                        ps.setNull(9, java.sql.Types.INTEGER);
                    } else {
                        ps.setInt(9, point.practiceMasteryRate());
                    }

                    ps.setInt(10, point.diagnosisLevel());

                    ps.setString(11, point.diagnosisLabel());

                    ps.setString(12, point.diagnosisReason());

                    ps.setString(13, point.suggestion());
                }
        );
    }

    /**
     * 生成阶段学习总结。
     */
    private String buildSummary(
            LocalDate startDate,
            LocalDate endDate,
            ExerciseStats exercise,
            long questionCount,
            TaskStats tasks,
            int weakPointCount) {

        StringBuilder text = new StringBuilder();

        text.append("统计周期：")
                .append(startDate)
                .append(" 至 ")
                .append(endDate)
                .append("。 ");

        text.append("本阶段提交练习 ")
                .append(exercise.exerciseCount())
                .append(" 次，客观题 ")
                .append(exercise.objectiveAnswerCount())
                .append(" 道");

        if (exercise.accuracyRate() == null) {

            text.append("，暂无可计算的客观题正确率。");

        } else {

            text.append("，客观题正确率为 ")
                    .append(exercise.accuracyRate())
                    .append("%。");
        }

        text.append("本阶段课程提问 ")
                .append(questionCount)
                .append(" 次。 ");

        text.append("本阶段创建的有效课程任务 ")
                .append(tasks.totalTaskCount())
                .append(" 项，其中当前已完成 ")
                .append(tasks.completedTaskCount())
                .append(" 项。 ");

        text.append("报告生成时识别到需要加强的知识点 ")
                .append(weakPointCount)
                .append(" 个。 ");

        text.append(
                "薄弱知识点为生成报告时的诊断快照："
                + "提问依据采用最近30天统计，"
                + "练习依据采用现有知识点掌握诊断结果。"
        );

        return text.toString();
    }

    /**
     * 生成阶段改进建议。
     */
    private String buildSuggestions(
            ExerciseStats exercise,
            TaskStats tasks,
            List<StudentWeakPointDiagnosticVO> weakPoints) {

        List<String> suggestions = new ArrayList<>();

        if (exercise.accuracyRate() == null) {

            suggestions.add(
                    "当前统计范围内暂无已判分客观题，"
                    + "建议完成课程练习后再观察掌握情况。"
            );

        } else if (
                exercise.accuracyRate()
                        .compareTo(BigDecimal.valueOf(60)) < 0) {

            suggestions.add(
                    "客观题正确率偏低，建议回顾对应课程资料，"
                    + "先完成基础题，再进行错题重练。"
            );

        } else if (
                exercise.accuracyRate()
                        .compareTo(BigDecimal.valueOf(85)) < 0) {

            suggestions.add(
                    "客观题正确率仍有提升空间，"
                    + "建议整理易错题并针对相关知识点进行巩固。"
            );

        } else {

            suggestions.add(
                    "客观题练习表现较好，建议保持练习频率，"
                    + "并继续巩固已学知识点。"
            );
        }

        if (tasks.completedTaskCount() < tasks.totalTaskCount()) {

            suggestions.add(
                    "仍有本阶段创建的课程任务未完成，"
                    + "建议优先完成未完成任务。"
            );
        }

        if (!weakPoints.isEmpty()) {

            String titles = weakPoints.stream()
                    .limit(5)
                    .map(StudentWeakPointDiagnosticVO::title)
                    .filter(Objects::nonNull)
                    .reduce((a, b) -> a + "、" + b)
                    .orElse("");

            if (!titles.isBlank()) {

                suggestions.add(
                        "建议优先复习以下知识点并完成针对性练习："
                        + titles
                        + "。"
                );
            }

        } else {

            suggestions.add(
                    "当前诊断未发现明显薄弱知识点，"
                    + "建议继续按照课程学习路径学习并保持练习。"
            );
        }

        return String.join("\n", suggestions);
    }

    /**
     * 查询报告历史列表。
     */
    private List<StudentLearningReportVO> queryReportList(
            String where,
            Object... args) {

        return jdbc.query(
                """
                SELECT
                    CAST(r.id AS CHAR) AS id,
                    CAST(r.student_id AS CHAR) AS student_id,
                    COALESCE(NULLIF(u.nickname, ''), u.username)
                        AS student_name,
                    CAST(r.course_id AS CHAR) AS course_id,
                    c.name AS course_name,
                    r.title,
                    r.period_start,
                    r.period_end,
                    r.exercise_count,
                    r.objective_answer_count,
                    r.correct_objective_count,
                    r.exercise_accuracy_rate,
                    r.question_count,
                    r.completed_task_count,
                    r.total_task_count,
                    r.weak_point_count,
                    r.created_at

                FROM student_learning_report r

                JOIN sys_user u
                  ON u.id = r.student_id

                JOIN course c
                  ON c.id = r.course_id
                """ + where + " ORDER BY r.created_at DESC, r.id DESC",
                (rs, rowNum) -> mapReport(rs),
                args
        );
    }

    /**
     * 查询单份报告。
     */
    private ReportRow findReport(
            String where,
            Object... args) {

        List<ReportRow> rows = jdbc.query(
                """
                SELECT
                    CAST(r.id AS CHAR) AS id,
                    CAST(r.student_id AS CHAR) AS student_id,
                    COALESCE(NULLIF(u.nickname, ''), u.username)
                        AS student_name,
                    CAST(r.course_id AS CHAR) AS course_id,
                    c.name AS course_name,
                    r.title,
                    r.period_start,
                    r.period_end,
                    r.exercise_count,
                    r.objective_answer_count,
                    r.correct_objective_count,
                    r.exercise_accuracy_rate,
                    r.question_count,
                    r.completed_task_count,
                    r.total_task_count,
                    r.weak_point_count,
                    r.report_summary,
                    r.improvement_suggestions,
                    r.created_at

                FROM student_learning_report r

                JOIN sys_user u
                  ON u.id = r.student_id

                JOIN course c
                  ON c.id = r.course_id
                """ + where,
                (rs, rowNum) -> new ReportRow(
                        rs.getString("id"),
                        rs.getString("student_id"),
                        rs.getString("student_name"),
                        rs.getString("course_id"),
                        rs.getString("course_name"),
                        rs.getString("title"),
                        rs.getDate("period_start").toLocalDate(),
                        rs.getDate("period_end").toLocalDate(),
                        rs.getLong("exercise_count"),
                        rs.getLong("objective_answer_count"),
                        rs.getLong("correct_objective_count"),
                        rs.getBigDecimal("exercise_accuracy_rate"),
                        rs.getLong("question_count"),
                        rs.getLong("completed_task_count"),
                        rs.getLong("total_task_count"),
                        rs.getInt("weak_point_count"),
                        rs.getString("report_summary"),
                        rs.getString("improvement_suggestions"),
                        toLocalDateTime(
                                rs.getTimestamp("created_at")
                        )
                ),
                args
        );

        if (rows.isEmpty()) {
            throw new BusinessException(404, "学习报告不存在或无权访问");
        }

        return rows.get(0);
    }

    /**
     * 报告列表结果映射。
     */
    private StudentLearningReportVO mapReport(
            java.sql.ResultSet rs)
            throws java.sql.SQLException {

        return new StudentLearningReportVO(
                rs.getString("id"),
                rs.getString("student_id"),
                rs.getString("student_name"),
                rs.getString("course_id"),
                rs.getString("course_name"),
                rs.getString("title"),
                rs.getDate("period_start").toLocalDate(),
                rs.getDate("period_end").toLocalDate(),
                rs.getLong("exercise_count"),
                rs.getLong("objective_answer_count"),
                rs.getLong("correct_objective_count"),
                rs.getBigDecimal("exercise_accuracy_rate"),
                rs.getLong("question_count"),
                rs.getLong("completed_task_count"),
                rs.getLong("total_task_count"),
                rs.getInt("weak_point_count"),
                toLocalDateTime(
                        rs.getTimestamp("created_at")
                )
        );
    }

    /**
     * 将报告主记录转换为报告详情。
     */
    private StudentLearningReportDetailVO toDetail(
            ReportRow report) {

        return new StudentLearningReportDetailVO(
                report.id(),
                report.studentId(),
                report.studentName(),
                report.courseId(),
                report.courseName(),
                report.title(),
                report.periodStart(),
                report.periodEnd(),
                report.exerciseCount(),
                report.objectiveAnswerCount(),
                report.correctObjectiveCount(),
                report.exerciseAccuracyRate(),
                report.questionCount(),
                report.completedTaskCount(),
                report.totalTaskCount(),
                report.weakPointCount(),
                report.reportSummary(),
                report.improvementSuggestions(),
                report.createdAt(),
                queryWeakPointSnapshots(
                        Long.valueOf(report.id())
                ),
                queryComments(
                        Long.valueOf(report.id())
                )
        );
    }

    /**
     * 查询报告生成时的薄弱知识点快照。
     */
    private List<WeakPointSnapshotVO> queryWeakPointSnapshots(
            Long reportId) {

        return jdbc.query(
                """
                SELECT
                    CAST(point_id AS CHAR) AS point_id,
                    point_title,
                    effective_question_count,
                    question_session_count,
                    question_inferred_level,
                    practice_question_count,
                    mastered_question_count,
                    practice_mastery_rate,
                    diagnosis_level,
                    diagnosis_label,
                    diagnosis_reason,
                    suggestion

                FROM student_learning_report_weak_point

                WHERE report_id = ?

                ORDER BY
                    diagnosis_level ASC,
                    effective_question_count DESC,
                    id
                """,
                (rs, rowNum) -> {

                    int rateValue =
                            rs.getInt("practice_mastery_rate");

                    Integer rate = rs.wasNull()
                            ? null
                            : rateValue;

                    return new WeakPointSnapshotVO(
                            rs.getString("point_id"),
                            rs.getString("point_title"),
                            rs.getInt("effective_question_count"),
                            rs.getInt("question_session_count"),
                            rs.getInt("question_inferred_level"),
                            rs.getInt("practice_question_count"),
                            rs.getInt("mastered_question_count"),
                            rate,
                            rs.getInt("diagnosis_level"),
                            rs.getString("diagnosis_label"),
                            rs.getString("diagnosis_reason"),
                            rs.getString("suggestion")
                    );
                },
                reportId
        );
    }

    /**
     * 查询教师批注历史。
     */
    private List<CommentVO> queryComments(
            Long reportId) {

        return jdbc.query(
                """
                SELECT
                    CAST(c.id AS CHAR) AS id,
                    CAST(c.teacher_id AS CHAR) AS teacher_id,
                    COALESCE(NULLIF(u.nickname, ''), u.username)
                        AS teacher_name,
                    c.comment_content,
                    c.created_at

                FROM student_learning_report_comment c

                JOIN sys_user u
                  ON u.id = c.teacher_id

                WHERE c.report_id = ?

                ORDER BY c.created_at ASC, c.id ASC
                """,
                (rs, rowNum) -> new CommentVO(
                        rs.getString("id"),
                        rs.getString("teacher_id"),
                        rs.getString("teacher_name"),
                        rs.getString("comment_content"),
                        toLocalDateTime(
                                rs.getTimestamp("created_at")
                        )
                ),
                reportId
        );
    }

    private LocalDateTime toLocalDateTime(
            Timestamp timestamp) {

        return timestamp == null
                ? null
                : timestamp.toLocalDateTime();
    }

    private record ExerciseStats(
            long exerciseCount,
            long objectiveAnswerCount,
            long correctObjectiveCount,
            BigDecimal accuracyRate
    ) {}

    private record TaskStats(
            long completedTaskCount,
            long totalTaskCount
    ) {}

    private record ReportRow(
            String id,
            String studentId,
            String studentName,
            String courseId,
            String courseName,
            String title,
            LocalDate periodStart,
            LocalDate periodEnd,
            long exerciseCount,
            long objectiveAnswerCount,
            long correctObjectiveCount,
            BigDecimal exerciseAccuracyRate,
            long questionCount,
            long completedTaskCount,
            long totalTaskCount,
            int weakPointCount,
            String reportSummary,
            String improvementSuggestions,
            LocalDateTime createdAt
    ) {}
}
