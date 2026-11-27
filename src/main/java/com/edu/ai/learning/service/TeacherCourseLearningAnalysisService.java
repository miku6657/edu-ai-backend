package com.edu.ai.learning.service;

import com.edu.ai.course.service.CoursePermissionService;
import com.edu.ai.learning.dto.TeacherCourseLearningAnalysisVO;
import com.edu.ai.learning.dto.TeacherCourseLearningAnalysisVO.ChapterProgress;
import com.edu.ai.learning.dto.TeacherCourseLearningAnalysisVO.MaterialSummary;
import com.edu.ai.learning.dto.TeacherCourseLearningAnalysisVO.MaterialUsage;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
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
                materials,
                chapters,
                lowCompletionChapters,
                LocalDateTime.now(APP_ZONE)
        );
    }

    private CourseSummary queryCourseSummary(Long courseId) {

        return jdbc.queryForObject(
                """
                SELECT
                    c.name AS course_name,

                    (
                        SELECT COUNT(*)
                        FROM course_student cs
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
                        JOIN course_student cs
                          ON cs.course_id = a.course_id
                         AND cs.student_id = a.student_id
                        WHERE a.course_id = c.id
                          AND a.status IN ('GRADED', 'PENDING_REVIEW')
                    ) AS submitted_attempt_count,

                    (
                        SELECT COUNT(DISTINCT a.student_id)
                        FROM course_exercise_attempt a
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
                SELECT COUNT(DISTINCT CONCAT(ts.student_id, ':', ts.task_id))

                FROM task_student ts

                JOIN course_student cs
                  ON cs.course_id = ?
                 AND cs.student_id = ts.student_id

                JOIN learning_task t
                  ON t.id = ts.task_id
                 AND t.course_id = cs.course_id
                 AND t.status = 1

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
