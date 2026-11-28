package com.edu.ai.learning.service;

import com.edu.ai.course.service.CoursePermissionService;
import com.edu.ai.learning.dto.TeacherCourseLearningAnalysisVO;
import com.edu.ai.learning.dto.TeacherCourseLearningAnalysisVO.ChapterProgress;
import com.edu.ai.learning.dto.TeacherCourseLearningAnalysisVO.KnowledgePointLearningStat;
import com.edu.ai.learning.dto.TeacherCourseLearningAnalysisVO.MaterialSummary;
import com.edu.ai.learning.dto.TeacherCourseLearningAnalysisVO.MaterialUsage;
import com.edu.ai.learning.dto.TeacherCourseLearningAnalysisVO.StudentProgress;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Service
public class TeacherCourseLearningAnalysisService {

    private static final ZoneId APP_ZONE =
            ZoneId.of("Asia/Shanghai");

    private static final BigDecimal LOW_COMPLETION_THRESHOLD =
            new BigDecimal("60.00");

    private final JdbcTemplate jdbc;
    private final CoursePermissionService permissions;

    public TeacherCourseLearningAnalysisService(
            JdbcTemplate jdbc,
            CoursePermissionService permissions) {
        this.jdbc = jdbc;
        this.permissions = permissions;
    }

    public TeacherCourseLearningAnalysisVO getCourseAnalysis(
            Long courseId,
            Long teacherId) {

        permissions.requireManage(courseId, teacherId);
        permissions.requireActiveRead(courseId, teacherId);

        CourseSummary summary = queryCourseSummary(courseId);

        long completedTaskAssignments =
                queryCompletedTaskAssignments(courseId);

        long taskAssignmentCount =
                summary.studentCount() * summary.activeTaskCount();

        BigDecimal taskCompletionRate = percentage(
                completedTaskAssignments,
                taskAssignmentCount
        );

        BigDecimal exerciseParticipationRate = percentage(
                summary.participatingStudentCount(),
                summary.studentCount()
        );

        MaterialSummary materialSummary =
                queryMaterialSummary(courseId);

        List<KnowledgePointLearningStat> knowledgePoints =
                queryKnowledgePointLearningStats(courseId);

        // 新增：逐个学生汇总学习数据
        List<StudentProgress> students =
                queryStudentProgress(courseId);

        List<MaterialUsage> materials =
                queryMaterialUsage(courseId);

        List<ChapterProgress> chapters =
                queryChapterProgress(courseId);

        List<ChapterProgress> lowCompletionChapters =
                chapters.stream()
                        .filter(ChapterProgress::lowCompletion)
                        .toList();

        return new TeacherCourseLearningAnalysisVO(
                Long.toString(courseId),
                summary.courseName(),
                summary.studentCount(),
                summary.activeTaskCount(),
                taskAssignmentCount,
                completedTaskAssignments,
                taskCompletionRate,
                summary.publishedExerciseCount(),
                summary.submittedAttemptCount(),
                summary.participatingStudentCount(),
                exerciseParticipationRate,
                materialSummary,
                knowledgePoints,
                students,
                materials,
                chapters,
                lowCompletionChapters,
                LocalDateTime.now(APP_ZONE)
        );
    }

    /**
     * 查询课程中每个有效学生的综合学习情况。
     *
     * 统计：
     * 1. 已提交练习次数。
     * 2. 客观题正确率。
     * 3. 普通课程提问次数。
     * 4. 课程任务完成情况。
     * 5. 资料预览、下载次数。
     * 6. 最近学习活动时间。
     *
     * 所有统计均按当前课程有效学生计算。
     */
    private List<StudentProgress> queryStudentProgress(
            Long courseId) {

        String sql = """
                WITH student_base AS (

                    SELECT DISTINCT
                        cs.student_id,

                        COALESCE(
                            NULLIF(u.nickname, ''),
                            u.username
                        ) AS student_name,

                        u.username

                    FROM course_student cs

                    JOIN sys_user u
                      ON u.id = cs.student_id
                     AND u.role = 'STUDENT'
                     AND u.status = 1

                    WHERE cs.course_id = ?
                ),

                task_stats AS (

                    SELECT
                        sb.student_id,

                        COUNT(DISTINCT t.id)
                            AS total_task_count,

                        COUNT(
                            DISTINCT CASE
                                WHEN ts.completed = 1
                                THEN t.id
                            END
                        ) AS completed_task_count,

                        MAX(
                            CASE
                                WHEN ts.completed = 1
                                THEN ts.completed_time
                            END
                        ) AS last_task_completed_at

                    FROM student_base sb

                    LEFT JOIN learning_task t
                      ON t.course_id = ?
                     AND t.status = 1

                    LEFT JOIN task_student ts
                      ON ts.task_id = t.id
                     AND ts.student_id = sb.student_id

                    GROUP BY sb.student_id
                ),

                exercise_stats AS (

                    SELECT
                        a.student_id,

                        COUNT(DISTINCT a.id)
                            AS submitted_attempt_count,

                        COUNT(q.id)
                            AS objective_answer_count,

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
                        ) AS correct_objective_count,

                        MAX(
                            COALESCE(a.submitted_at, a.started_at)
                        ) AS last_practice_at

                    FROM course_exercise_attempt a

                    JOIN student_base sb
                      ON sb.student_id = a.student_id

                    LEFT JOIN course_exercise_attempt_answer aa
                      ON aa.attempt_id = a.id
                     AND aa.is_correct IS NOT NULL

                    LEFT JOIN course_exercise_question q
                      ON q.id = aa.question_id
                     AND q.question_type <> 'SHORT_ANSWER'

                    WHERE a.course_id = ?
                      AND a.status IN ('GRADED', 'PENDING_REVIEW')

                    GROUP BY a.student_id
                ),

                question_stats AS (

                    SELECT
                        s.user_id AS student_id,

                        COUNT(DISTINCT m.id)
                            AS question_count,

                        MAX(m.create_time)
                            AS last_question_at

                    FROM chat_session s

                    JOIN student_base sb
                      ON sb.student_id = s.user_id

                    JOIN chat_message m
                      ON m.session_id = s.id
                     AND m.role = 'user'

                    WHERE s.course_id = ?

                      AND NOT EXISTS (
                          SELECT 1
                          FROM course_exercise_explain_session ees
                          WHERE ees.chat_session_id = s.id
                      )

                    GROUP BY s.user_id
                ),

                material_stats AS (

                    SELECT
                        l.student_id,

                        COUNT(*) AS material_access_count,

                        MAX(l.accessed_at)
                            AS last_material_access_at

                    FROM course_document_access_log l

                    WHERE l.course_id = ?

                    GROUP BY l.student_id
                )

                SELECT
                    CAST(sb.student_id AS CHAR)
                        AS student_id,

                    sb.student_name,

                    sb.username,

                    COALESCE(es.submitted_attempt_count, 0)
                        AS submitted_attempt_count,

                    COALESCE(es.objective_answer_count, 0)
                        AS objective_answer_count,

                    COALESCE(es.correct_objective_count, 0)
                        AS correct_objective_count,

                    CASE
                        WHEN COALESCE(es.objective_answer_count, 0) = 0
                        THEN NULL

                        ELSE ROUND(
                            es.correct_objective_count
                            * 100.0
                            / es.objective_answer_count,
                            2
                        )
                    END AS exercise_accuracy_rate,

                    COALESCE(qs.question_count, 0)
                        AS question_count,

                    COALESCE(ts.completed_task_count, 0)
                        AS completed_task_count,

                    COALESCE(ts.total_task_count, 0)
                        AS total_task_count,

                    CASE
                        WHEN COALESCE(ts.total_task_count, 0) = 0
                        THEN NULL

                        ELSE ROUND(
                            ts.completed_task_count
                            * 100.0
                            / ts.total_task_count,
                            2
                        )
                    END AS task_completion_rate,

                    COALESCE(ms.material_access_count, 0)
                        AS material_access_count,

                    CASE
                        WHEN es.last_practice_at IS NULL
                         AND qs.last_question_at IS NULL
                         AND ts.last_task_completed_at IS NULL
                         AND ms.last_material_access_at IS NULL
                        THEN NULL

                        ELSE GREATEST(
                            COALESCE(
                                es.last_practice_at,
                                CAST('1000-01-01 00:00:00' AS DATETIME)
                            ),
                            COALESCE(
                                qs.last_question_at,
                                CAST('1000-01-01 00:00:00' AS DATETIME)
                            ),
                            COALESCE(
                                ts.last_task_completed_at,
                                CAST('1000-01-01 00:00:00' AS DATETIME)
                            ),
                            COALESCE(
                                ms.last_material_access_at,
                                CAST('1000-01-01 00:00:00' AS DATETIME)
                            )
                        )
                    END AS last_active_at

                FROM student_base sb

                LEFT JOIN task_stats ts
                  ON ts.student_id = sb.student_id

                LEFT JOIN exercise_stats es
                  ON es.student_id = sb.student_id

                LEFT JOIN question_stats qs
                  ON qs.student_id = sb.student_id

                LEFT JOIN material_stats ms
                  ON ms.student_id = sb.student_id

                ORDER BY
                    CASE
                        WHEN COALESCE(ts.total_task_count, 0) = 0
                        THEN 1
                        ELSE 0
                    END,

                    COALESCE(ts.completed_task_count, 0)
                        * 1.0
                        / NULLIF(ts.total_task_count, 0),

                    CASE
                        WHEN COALESCE(es.objective_answer_count, 0) = 0
                        THEN 1
                        ELSE 0
                    END,

                    COALESCE(es.correct_objective_count, 0)
                        * 1.0
                        / NULLIF(es.objective_answer_count, 0),

                    sb.student_id
                """;

        LocalDateTime analyzedAt = LocalDateTime.now(APP_ZONE);

        return jdbc.query(
                sql,
                (rs, rowNum) -> {

                    Timestamp lastActive =
                            rs.getTimestamp("last_active_at");

                    LocalDateTime lastActiveAt =
                            lastActive == null
                                    ? null
                                    : lastActive.toLocalDateTime();

                    BigDecimal accuracyRate =
                            rs.getBigDecimal("exercise_accuracy_rate");

                    BigDecimal taskRate =
                            rs.getBigDecimal("task_completion_rate");

                    List<String> statusReasons = new ArrayList<>();

                    // 1. 任务完成率预警
                    if (taskRate != null
                            && taskRate.compareTo(LOW_COMPLETION_THRESHOLD) < 0) {

                        statusReasons.add(
                                "任务完成率低于60%，当前为"
                                        + taskRate.stripTrailingZeros().toPlainString()
                                        + "%"
                        );
                    }

                    // 2. 客观题正确率预警
                    if (accuracyRate != null
                            && accuracyRate.compareTo(LOW_COMPLETION_THRESHOLD) < 0) {

                        statusReasons.add(
                                "客观题正确率低于60%，当前为"
                                        + accuracyRate.stripTrailingZeros().toPlainString()
                                        + "%"
                        );
                    }

                    // 3. 学习活动时间预警
                    if (lastActiveAt == null) {

                        statusReasons.add("暂无可统计的学习活动");

                    } else if (lastActiveAt.isBefore(
                            analyzedAt.minusDays(14))) {

                        statusReasons.add("连续14天没有学习活动");
                    }

                    // 4. 综合学习状态
                    String learningStatus;

                    boolean hasLearningRisk =
                            taskRate != null
                                    && taskRate.compareTo(
                                            LOW_COMPLETION_THRESHOLD
                                    ) < 0
                            ||
                            accuracyRate != null
                                    && accuracyRate.compareTo(
                                            LOW_COMPLETION_THRESHOLD
                                    ) < 0
                            ||
                            lastActiveAt != null
                                    && lastActiveAt.isBefore(
                                            analyzedAt.minusDays(14)
                                    );

                    if (hasLearningRisk) {
                        learningStatus = "ATTENTION";
                    } else if (lastActiveAt == null) {
                        learningStatus = "NO_DATA";
                    } else {
                        learningStatus = "NORMAL";
                    }

                    return new StudentProgress(
                            rs.getString("student_id"),
                            rs.getString("student_name"),
                            rs.getString("username"),

                            rs.getLong("submitted_attempt_count"),

                            rs.getLong("objective_answer_count"),

                            rs.getLong("correct_objective_count"),

                            accuracyRate,

                            rs.getLong("question_count"),

                            rs.getLong("completed_task_count"),

                            rs.getLong("total_task_count"),

                            taskRate,

                            rs.getLong("material_access_count"),

                            lastActiveAt,

                            learningStatus,

                            statusReasons
                    );
                },
                courseId,
                courseId,
                courseId,
                courseId,
                courseId
        );
    }

    private CourseSummary queryCourseSummary(Long courseId) {

        return jdbc.queryForObject(
                """
                SELECT
                    c.name AS course_name,

                    (
                        SELECT COUNT(DISTINCT cs.student_id)
                        FROM course_student cs
                        JOIN sys_user u
                          ON u.id = cs.student_id
                         AND u.role = 'STUDENT'
                         AND u.status = 1
                        WHERE cs.course_id = c.id
                    ) AS student_count,

                    (
                        SELECT COUNT(*)
                        FROM learning_task t
                        WHERE t.course_id = c.id
                          AND t.status = 1
                    ) AS active_task_count,

                    (
                        SELECT COUNT(*)
                        FROM course_exercise e
                        WHERE e.course_id = c.id
                          AND e.status = 'PUBLISHED'
                    ) AS published_exercise_count,

                    (
                        SELECT COUNT(*)
                        FROM course_exercise_attempt a
                        JOIN sys_user u
                          ON u.id = a.student_id
                         AND u.role = 'STUDENT'
                         AND u.status = 1
                        JOIN course_student cs
                          ON cs.course_id = a.course_id
                         AND cs.student_id = a.student_id
                        WHERE a.course_id = c.id
                          AND a.status IN ('GRADED', 'PENDING_REVIEW')
                    ) AS submitted_attempt_count,

                    (
                        SELECT COUNT(DISTINCT a.student_id)
                        FROM course_exercise_attempt a
                        JOIN sys_user u
                          ON u.id = a.student_id
                         AND u.role = 'STUDENT'
                         AND u.status = 1
                        JOIN course_student cs
                          ON cs.course_id = a.course_id
                         AND cs.student_id = a.student_id
                        WHERE a.course_id = c.id
                          AND a.status IN ('GRADED', 'PENDING_REVIEW')
                    ) AS participating_student_count

                FROM course c
                WHERE c.id = ?
                """,
                (rs, rowNum) -> new CourseSummary(
                        rs.getString("course_name"),
                        rs.getLong("student_count"),
                        rs.getLong("active_task_count"),
                        rs.getLong("published_exercise_count"),
                        rs.getLong("submitted_attempt_count"),
                        rs.getLong("participating_student_count")
                ),
                courseId
        );
    }

    private long queryCompletedTaskAssignments(Long courseId) {

        Long count = jdbc.queryForObject(
                """
                SELECT COUNT(DISTINCT ts.id)

                FROM task_student ts

                JOIN learning_task t
                  ON t.id = ts.task_id
                 AND t.course_id = ?
                 AND t.status = 1

                JOIN course_student cs
                  ON cs.course_id = t.course_id
                 AND cs.student_id = ts.student_id

                JOIN sys_user u
                  ON u.id = cs.student_id
                 AND u.role = 'STUDENT'
                 AND u.status = 1

                WHERE ts.completed = 1
                """,
                Long.class,
                courseId
        );

        return count == null ? 0L : count;
    }

    private MaterialSummary queryMaterialSummary(Long courseId) {

        return jdbc.queryForObject(
                """
                SELECT
                    COUNT(DISTINCT d.id) AS document_count,

                    COUNT(DISTINCT CASE
                        WHEN l.id IS NOT NULL THEN d.id
                    END) AS accessed_document_count,

                    COUNT(l.id) AS access_count,

                    COALESCE(SUM(CASE
                        WHEN l.access_type = 'PREVIEW' THEN 1
                        ELSE 0
                    END), 0) AS preview_count,

                    COALESCE(SUM(CASE
                        WHEN l.access_type = 'DOWNLOAD' THEN 1
                        ELSE 0
                    END), 0) AS download_count,

                    COUNT(DISTINCT l.student_id) AS active_student_count

                FROM course_document d

                LEFT JOIN course_document_access_log l
                  ON l.document_id = d.id
                 AND l.course_id = d.course_id

                WHERE d.course_id = ?
                  AND d.status = 'READY'
                  AND d.version_status = 'CURRENT'
                  AND d.review_status = 'APPROVED'
                """,
                (rs, rowNum) -> new MaterialSummary(
                        rs.getLong("document_count"),
                        rs.getLong("accessed_document_count"),
                        rs.getLong("access_count"),
                        rs.getLong("preview_count"),
                        rs.getLong("download_count"),
                        rs.getLong("active_student_count")
                ),
                courseId
        );
    }

    /**
     * 统计课程各知识点的综合学习情况。
     *
     * 1. 学生提问热度。
     * 2. 原始练习和错题重练的最新掌握结果。
     * 3. 学生对知识点的自评情况。
     *
     * 同一个学生对同一道题重复作答，只保留最近一次结果。
     */
    private List<KnowledgePointLearningStat> queryKnowledgePointLearningStats(
            Long courseId) {

        String sql = """
                WITH active_students AS (

                    SELECT DISTINCT
                        cs.course_id,
                        cs.student_id

                    FROM course_student cs

                    JOIN sys_user u
                      ON u.id = cs.student_id
                     AND u.role = 'STUDENT'
                     AND u.status = 1

                    WHERE cs.course_id = ?
                ),

                all_practice AS (

                    -- 原始课程练习
                    SELECT
                        a.course_id,
                        kp.id AS knowledge_point_id,
                        q.id AS question_id,
                        a.student_id,

                        CASE
                            WHEN aa.is_correct = 1 THEN 1
                            ELSE 0
                        END AS mastered_flag,

                        COALESCE(
                            aa.graded_at,
                            a.graded_at,
                            a.submitted_at,
                            aa.created_at
                        ) AS practiced_at,

                        a.id AS attempt_id,

                        1 AS source_priority

                    FROM course_exercise_attempt_answer aa

                    JOIN course_exercise_attempt a
                      ON a.id = aa.attempt_id

                    JOIN course_exercise e
                      ON e.id = a.exercise_id
                     AND e.course_id = a.course_id

                    JOIN course_exercise_question q
                      ON q.id = aa.question_id
                     AND q.exercise_id = e.id

                    JOIN course_knowledge_point kp
                      ON kp.id = q.knowledge_point_id
                     AND kp.course_id = a.course_id

                    JOIN active_students ast
                      ON ast.course_id = a.course_id
                     AND ast.student_id = a.student_id

                    WHERE a.status = 'GRADED'
                      AND aa.pending_review = 0
                      AND aa.is_correct IS NOT NULL

                    UNION ALL

                    -- 错题重练
                    SELECT
                        e.course_id,
                        kp.id AS knowledge_point_id,
                        q.id AS question_id,
                        a.student_id,

                        CASE
                            WHEN q.question_type = 'SHORT_ANSWER'
                             AND aa.self_review_understood = 1
                            THEN 1

                            WHEN q.question_type <> 'SHORT_ANSWER'
                             AND aa.is_correct = 1
                            THEN 1

                            ELSE 0
                        END AS mastered_flag,

                        COALESCE(
                            aa.self_reviewed_at,
                            a.graded_at,
                            a.submitted_at,
                            aa.updated_at
                        ) AS practiced_at,

                        a.id AS attempt_id,

                        2 AS source_priority

                    FROM course_exercise_retry_attempt_answer aa

                    JOIN course_exercise_retry_attempt a
                      ON a.id = aa.attempt_id

                    JOIN course_exercise_retry_list rl
                      ON rl.id = a.retry_list_id
                     AND rl.student_id = a.student_id

                    JOIN course_exercise_question q
                      ON q.id = aa.question_id

                    JOIN course_exercise e
                      ON e.id = q.exercise_id

                    JOIN course_knowledge_point kp
                      ON kp.id = q.knowledge_point_id
                     AND kp.course_id = e.course_id

                    JOIN active_students ast
                      ON ast.course_id = e.course_id
                     AND ast.student_id = a.student_id

                    WHERE a.status = 'GRADED'

                      AND (
                          (
                              q.question_type = 'SHORT_ANSWER'
                              AND aa.self_review_understood IS NOT NULL
                          )
                          OR
                          (
                              q.question_type <> 'SHORT_ANSWER'
                              AND aa.is_correct IS NOT NULL
                          )
                      )
                ),

                ranked_practice AS (

                    SELECT
                        *,

                        ROW_NUMBER() OVER (
                            PARTITION BY
                                course_id,
                                student_id,
                                question_id

                            ORDER BY
                                practiced_at DESC,
                                source_priority DESC,
                                attempt_id DESC
                        ) AS rn

                    FROM all_practice
                ),

                practice_stats AS (

                    SELECT
                        course_id,
                        knowledge_point_id,

                        COUNT(*) AS practice_question_count,

                        SUM(mastered_flag)
                            AS mastered_question_count,

                        COUNT(DISTINCT student_id)
                            AS practice_student_count,

                        ROUND(
                            SUM(mastered_flag) * 100.0 / COUNT(*),
                            2
                        ) AS practice_mastery_rate,

                        MAX(practiced_at)
                            AS last_practiced_at

                    FROM ranked_practice

                    WHERE rn = 1

                    GROUP BY
                        course_id,
                        knowledge_point_id
                ),

                question_stats AS (

                    SELECT
                        qk.course_id,
                        qk.point_id AS knowledge_point_id,

                        COUNT(DISTINCT m.id)
                            AS question_count,

                        COUNT(DISTINCT s.user_id)
                            AS question_student_count,

                        MAX(m.create_time)
                            AS latest_asked_at

                    FROM student_question_knowledge qk

                    JOIN course_knowledge_point kp
                      ON kp.id = qk.point_id
                     AND kp.course_id = qk.course_id

                    JOIN chat_message m
                      ON m.id = qk.chat_message_id
                     AND m.session_id = qk.session_id
                     AND m.role = 'user'

                    JOIN chat_session s
                      ON s.id = m.session_id
                     AND s.course_id = qk.course_id
                     AND s.user_id = qk.student_id

                    JOIN active_students ast
                      ON ast.course_id = qk.course_id
                     AND ast.student_id = s.user_id

                    WHERE NOT EXISTS (
                        SELECT 1
                        FROM course_exercise_explain_session ees
                        WHERE ees.chat_session_id = s.id
                    )

                    GROUP BY
                        qk.course_id,
                        qk.point_id
                ),

                self_stats AS (

                    SELECT
                        ast.course_id,
                        sm.point_id AS knowledge_point_id,

                        COUNT(DISTINCT sm.student_id)
                            AS self_assessment_count,

                        ROUND(
                            AVG(sm.mastery_level),
                            2
                        ) AS average_self_assessment_level

                    FROM student_knowledge_mastery sm

                    JOIN active_students ast
                      ON ast.student_id = sm.student_id

                    JOIN course_knowledge_point kp
                      ON kp.id = sm.point_id
                     AND kp.course_id = ast.course_id

                    GROUP BY
                        ast.course_id,
                        sm.point_id
                )

                SELECT
                    CAST(p.id AS CHAR) AS point_id,
                    p.title,

                    COALESCE(qs.question_count, 0)
                        AS question_count,

                    COALESCE(qs.question_student_count, 0)
                        AS question_student_count,

                    COALESCE(ps.practice_question_count, 0)
                        AS practice_question_count,

                    COALESCE(ps.mastered_question_count, 0)
                        AS mastered_question_count,

                    ps.practice_mastery_rate,

                    COALESCE(ps.practice_student_count, 0)
                        AS practice_student_count,

                    COALESCE(ss.self_assessment_count, 0)
                        AS self_assessment_count,

                    ss.average_self_assessment_level,

                    qs.latest_asked_at,

                    ps.last_practiced_at

                FROM course_knowledge_point p

                LEFT JOIN question_stats qs
                  ON qs.course_id = p.course_id
                 AND qs.knowledge_point_id = p.id

                LEFT JOIN practice_stats ps
                  ON ps.course_id = p.course_id
                 AND ps.knowledge_point_id = p.id

                LEFT JOIN self_stats ss
                  ON ss.course_id = p.course_id
                 AND ss.knowledge_point_id = p.id

                WHERE p.course_id = ?

                ORDER BY
                    COALESCE(qs.question_count, 0) DESC,
                    CASE
                        WHEN ps.practice_mastery_rate IS NULL THEN 1
                        ELSE 0
                    END,
                    ps.practice_mastery_rate ASC,
                    p.sort_order,
                    p.id
                """;

        return jdbc.query(
                sql,
                (rs, rowNum) -> {

                    Timestamp latestAsked =
                            rs.getTimestamp("latest_asked_at");

                    Timestamp lastPracticed =
                            rs.getTimestamp("last_practiced_at");

                    return new KnowledgePointLearningStat(
                            rs.getString("point_id"),
                            rs.getString("title"),

                            rs.getLong("question_count"),
                            rs.getLong("question_student_count"),

                            rs.getLong("practice_question_count"),
                            rs.getLong("mastered_question_count"),

                            rs.getBigDecimal("practice_mastery_rate"),

                            rs.getLong("practice_student_count"),

                            rs.getLong("self_assessment_count"),

                            rs.getBigDecimal(
                                    "average_self_assessment_level"
                            ),

                            latestAsked == null
                                    ? null
                                    : latestAsked.toLocalDateTime(),

                            lastPracticed == null
                                    ? null
                                    : lastPracticed.toLocalDateTime()
                    );
                },
                courseId,
                courseId
        );
    }

    private List<MaterialUsage> queryMaterialUsage(Long courseId) {

        // 使用相关子查询，避免资料关联多个章节时，
        // 与访问日志连接产生重复计数。
        return jdbc.query(
                """
                SELECT
                    d.id AS document_id,
                    d.original_name AS document_name,
                    d.resource_type,

                    (
                        SELECT GROUP_CONCAT(
                            DISTINCT ch.chapter_name
                            ORDER BY ch.sort_order, ch.id
                            SEPARATOR '、'
                        )
                        FROM course_document_chapter dc
                        JOIN course_chapter ch
                          ON ch.id = dc.chapter_id
                         AND ch.course_id = d.course_id
                        WHERE dc.document_id = d.id
                    ) AS chapter_names,

                    (
                        SELECT COUNT(*)
                        FROM course_document_access_log l
                        WHERE l.document_id = d.id
                          AND l.course_id = d.course_id
                          AND l.access_type = 'PREVIEW'
                    ) AS preview_count,

                    (
                        SELECT COUNT(*)
                        FROM course_document_access_log l
                        WHERE l.document_id = d.id
                          AND l.course_id = d.course_id
                          AND l.access_type = 'DOWNLOAD'
                    ) AS download_count,

                    (
                        SELECT COUNT(*)
                        FROM course_document_access_log l
                        WHERE l.document_id = d.id
                          AND l.course_id = d.course_id
                    ) AS total_access_count,

                    (
                        SELECT COUNT(DISTINCT l.student_id)
                        FROM course_document_access_log l
                        WHERE l.document_id = d.id
                          AND l.course_id = d.course_id
                    ) AS student_count,

                    (
                        SELECT MAX(l.accessed_at)
                        FROM course_document_access_log l
                        WHERE l.document_id = d.id
                          AND l.course_id = d.course_id
                    ) AS last_access_at

                FROM course_document d

                WHERE d.course_id = ?
                  AND d.status = 'READY'
                  AND d.version_status = 'CURRENT'
                  AND d.review_status = 'APPROVED'

                ORDER BY total_access_count DESC, d.id DESC
                """,
                (rs, rowNum) -> {

                    Timestamp lastAccess =
                            rs.getTimestamp("last_access_at");

                    return new MaterialUsage(
                            rs.getString("document_id"),
                            rs.getString("document_name"),
                            rs.getString("resource_type"),
                            rs.getString("chapter_names"),
                            rs.getLong("preview_count"),
                            rs.getLong("download_count"),
                            rs.getLong("total_access_count"),
                            rs.getLong("student_count"),
                            lastAccess == null
                                    ? null
                                    : lastAccess.toLocalDateTime()
                    );
                },
                courseId
        );
    }

    /**
     * 章节练习覆盖率：
     *
     * 分子：至少提交过该题一次的学生-题目组合数。
     * 分母：当前班级学生数 × 章节已发布练习题数量。
     *
     * 同一学生重复作答同一道题只计一次。
     */
    private List<ChapterProgress> queryChapterProgress(Long courseId) {

        return jdbc.query(
                """
                SELECT
                    ch.id AS chapter_id,
                    ch.chapter_name,

                    COUNT(DISTINCT q.id) AS question_count,

                    COUNT(DISTINCT cs.student_id) AS student_count,

                    COUNT(DISTINCT CASE
                        WHEN q.id IS NOT NULL
                         AND cs.student_id IS NOT NULL
                        THEN CONCAT(cs.student_id, ':', q.id)
                    END) AS total_expected_answers,

                    COUNT(DISTINCT CASE
                        WHEN aa.id IS NOT NULL
                        THEN CONCAT(cs.student_id, ':', q.id)
                    END) AS completed_student_question_count

                FROM course_chapter ch

                LEFT JOIN course_knowledge_point kp
                  ON kp.chapter_id = ch.id
                 AND kp.course_id = ch.course_id

                LEFT JOIN course_exercise e
                  ON e.course_id = ch.course_id
                 AND e.status = 'PUBLISHED'

                LEFT JOIN course_exercise_question q
                  ON q.exercise_id = e.id
                 AND q.knowledge_point_id = kp.id

                LEFT JOIN course_student cs
                  ON cs.course_id = ch.course_id
                 AND EXISTS (
                     SELECT 1
                     FROM sys_user u
                     WHERE u.id = cs.student_id
                       AND u.role = 'STUDENT'
                       AND u.status = 1
                 )

                LEFT JOIN course_exercise_attempt a
                  ON a.exercise_id = e.id
                 AND a.course_id = ch.course_id
                 AND a.student_id = cs.student_id
                 AND a.status IN ('GRADED', 'PENDING_REVIEW')

                LEFT JOIN course_exercise_attempt_answer aa
                  ON aa.attempt_id = a.id
                 AND aa.question_id = q.id

                WHERE ch.course_id = ?

                GROUP BY
                    ch.id,
                    ch.chapter_name,
                    ch.sort_order

                ORDER BY ch.sort_order, ch.id
                """,
                (rs, rowNum) -> {

                    long questionCount =
                            rs.getLong("question_count");

                    long studentCount =
                            rs.getLong("student_count");

                    long expected =
                            rs.getLong("total_expected_answers");

                    long completed =
                            rs.getLong("completed_student_question_count");

                    BigDecimal rate = percentage(
                            completed,
                            expected
                    );

                    boolean low = rate != null
                            && rate.compareTo(
                                    LOW_COMPLETION_THRESHOLD
                            ) < 0;

                    return new ChapterProgress(
                            rs.getString("chapter_id"),
                            rs.getString("chapter_name"),
                            questionCount,
                            studentCount,
                            expected,
                            completed,
                            rate,
                            low
                    );
                },
                courseId
        );
    }

    private BigDecimal percentage(
            long numerator,
            long denominator) {

        if (denominator <= 0) {
            return null;
        }

        return BigDecimal.valueOf(numerator)
                .multiply(BigDecimal.valueOf(100))
                .divide(
                        BigDecimal.valueOf(denominator),
                        2,
                        RoundingMode.HALF_UP
                );
    }

    private record CourseSummary(
            String courseName,
            long studentCount,
            long activeTaskCount,
            long publishedExerciseCount,
            long submittedAttemptCount,
            long participatingStudentCount
    ) {}
}
