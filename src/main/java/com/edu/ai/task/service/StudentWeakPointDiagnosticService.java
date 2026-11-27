package com.edu.ai.task.service;

import com.edu.ai.course.service.CoursePermissionService;
import com.edu.ai.task.dto.StudentWeakPointDiagnosticVO;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class StudentWeakPointDiagnosticService {

    private final JdbcTemplate jdbc;

    private final CoursePermissionService permissions;

    private final QuestionKnowledgeService questionKnowledgeService;

    public StudentWeakPointDiagnosticService(
            JdbcTemplate jdbc,
            CoursePermissionService permissions,
            QuestionKnowledgeService questionKnowledgeService) {

        this.jdbc = jdbc;
        this.permissions = permissions;
        this.questionKnowledgeService = questionKnowledgeService;
    }

    /**
     * 查询课程全部知识点的综合诊断。
     */
    public List<StudentWeakPointDiagnosticVO> diagnose(
            Long courseId,
            Long studentId) {

        permissions.requireActiveRead(courseId, studentId);

        // 1. 获取最近30天提问薄弱点
        Map<Long, QuestionKnowledgeService.WeakPoint> questionMap =
                new HashMap<>();

        for (QuestionKnowledgeService.WeakPoint point :
                questionKnowledgeService.listWeakPoints(
                        courseId,
                        studentId)) {

            questionMap.put(
                    Long.valueOf(point.pointId()),
                    point
            );
        }

        // 2. 获取练习掌握率和学生自评
        List<PracticePoint> practicePoints =
                queryPracticePoints(courseId, studentId);

        // 3. 合并两种数据
        List<StudentWeakPointDiagnosticVO> result =
                new ArrayList<>();

        for (PracticePoint practice : practicePoints) {

            QuestionKnowledgeService.WeakPoint question =
                    questionMap.get(practice.pointId());

            int effectiveCount = question == null
                    ? 0
                    : question.effectiveCount();

            int sessionCount = question == null
                    ? 0
                    : question.sessionCount();

            int questionLevel = question == null
                    ? 2
                    : question.inferredLevel();

            Integer practiceLevel = calculatePracticeLevel(
                    practice.practiceQuestionCount(),
                    practice.practiceMasteryRate()
            );

            // 4. 综合诊断：取有有效数据的证据中较弱的等级
            int diagnosisLevel = questionLevel;

            if (practiceLevel != null) {
                diagnosisLevel = Math.min(
                        diagnosisLevel,
                        practiceLevel
                );
            }

            String diagnosisLabel =
                    diagnosisLabel(diagnosisLevel);

            String reason = buildDiagnosisReason(
                    effectiveCount,
                    sessionCount,
                    questionLevel,
                    practice.practiceQuestionCount(),
                    practice.practiceMasteryRate()
            );

            String suggestion =
                    buildSuggestion(diagnosisLevel);

            result.add(
                    new StudentWeakPointDiagnosticVO(
                            String.valueOf(practice.pointId()),
                            practice.title(),
                            effectiveCount,
                            sessionCount,
                            questionLevel,
                            practice.practiceQuestionCount(),
                            practice.masteredQuestionCount(),
                            practice.practiceMasteryRate(),
                            practice.selfAssessmentLevel(),
                            practiceLevel,
                            diagnosisLevel,
                            diagnosisLabel,
                            reason,
                            suggestion,
                            practice.lastPracticedAt()
                    )
            );
        }

        return result;
    }

    /**
     * 查询学生各知识点练习掌握情况。
     *
     * 与原有练习推荐服务保持一致：
     * 1. 原始练习和错题重练合并。
     * 2. 同一道题只采用最近一次完成结果。
     * 3. 未完成批改的数据不参与统计。
     */
    private List<PracticePoint> queryPracticePoints(
            Long courseId,
            Long studentId) {

        String sql = """
                WITH all_practice AS (

                    -- 原始练习
                    SELECT
                        c.id AS course_id,
                        kp.id AS knowledge_point_id,
                        q.id AS question_id,

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

                    JOIN course_exercise_question q
                      ON q.id = aa.question_id

                    JOIN course c
                      ON c.id = a.course_id

                    JOIN course_knowledge_point kp
                      ON kp.id = q.knowledge_point_id
                     AND kp.course_id = c.id

                    WHERE a.student_id = ?
                      AND a.course_id = ?
                      AND a.status = 'GRADED'
                      AND aa.pending_review = 0
                      AND aa.is_correct IS NOT NULL

                    UNION ALL

                    -- 错题重练
                    SELECT
                        c.id AS course_id,
                        kp.id AS knowledge_point_id,
                        q.id AS question_id,

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

                    JOIN course c
                      ON c.id = e.course_id

                    JOIN course_knowledge_point kp
                      ON kp.id = q.knowledge_point_id
                     AND kp.course_id = c.id

                    WHERE a.student_id = ?
                      AND e.course_id = ?
                      AND a.status = 'GRADED'

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
                                question_id

                            ORDER BY
                                practiced_at DESC,
                                source_priority DESC,
                                attempt_id DESC
                        ) AS rn

                    FROM all_practice
                ),

                mastery AS (

                    SELECT
                        course_id,
                        knowledge_point_id,

                        COUNT(*) AS question_count,

                        SUM(mastered_flag) AS mastered_count,

                        ROUND(
                            SUM(mastered_flag) * 100.0 / COUNT(*)
                        ) AS mastery_rate,

                        MAX(practiced_at) AS last_practiced_at

                    FROM ranked_practice

                    WHERE rn = 1

                    GROUP BY
                        course_id,
                        knowledge_point_id
                )

                SELECT
                    p.id AS point_id,
                    p.title,

                    COALESCE(m.question_count, 0)
                        AS practice_question_count,

                    COALESCE(m.mastered_count, 0)
                        AS mastered_question_count,

                    m.mastery_rate,

                    COALESCE(sm.mastery_level, 2)
                        AS self_assessment_level,

                    m.last_practiced_at

                FROM course_knowledge_point p

                LEFT JOIN mastery m
                  ON m.course_id = p.course_id
                 AND m.knowledge_point_id = p.id

                LEFT JOIN student_knowledge_mastery sm
                  ON sm.point_id = p.id
                 AND sm.student_id = ?

                WHERE p.course_id = ?

                ORDER BY p.sort_order, p.id
                """;

        return jdbc.query(
                sql,
                (rs, rowNum) -> {

                    int rateValue =
                            rs.getInt("mastery_rate");

                    Integer rate = rs.wasNull()
                            ? null
                            : rateValue;

                    Timestamp timestamp =
                            rs.getTimestamp("last_practiced_at");

                    return new PracticePoint(
                            rs.getLong("point_id"),
                            rs.getString("title"),
                            rs.getInt("practice_question_count"),
                            rs.getInt("mastered_question_count"),
                            rate,
                            rs.getInt("self_assessment_level"),
                            timestamp == null
                                    ? null
                                    : timestamp.toLocalDateTime()
                    );
                },
                studentId,
                courseId,
                studentId,
                courseId,
                studentId,
                courseId
        );
    }

    /**
     * 练习掌握程度转换。
     *
     * 0：小于60%
     * 1：60%-84%
     * 2：85%及以上
     * null：没有练习记录
     */
    private Integer calculatePracticeLevel(
            int questionCount,
            Integer masteryRate) {

        if (questionCount == 0 || masteryRate == null) {
            return null;
        }

        if (masteryRate < 60) {
            return 0;
        }

        if (masteryRate < 85) {
            return 1;
        }

        return 2;
    }

    private String diagnosisLabel(int level) {
        return switch (level) {
            case 0 -> "重点巩固";
            case 1 -> "建议加强";
            default -> "暂无明显薄弱证据";
        };
    }

    private String buildDiagnosisReason(
            int effectiveCount,
            int sessionCount,
            int questionLevel,
            int practiceCount,
            Integer masteryRate) {

        List<String> reasons = new ArrayList<>();

        if (practiceCount == 0 || masteryRate == null) {
            reasons.add("暂无已判分的练习记录");
        } else {
            reasons.add(
                    "练习掌握率为 " + masteryRate
                            + "%，共统计 " + practiceCount + " 道题"
            );
        }

        if (effectiveCount > 0) {
            reasons.add(
                    "最近30天有效提问 "
                            + effectiveCount
                            + " 次，涉及 "
                            + sessionCount
                            + " 个会话"
            );
        } else {
            reasons.add("最近30天没有识别到有效提问");
        }

        if (questionLevel == 0) {
            reasons.add("提问频率达到重点复习候选标准");
        } else if (questionLevel == 1) {
            reasons.add("提问频率提示可能存在理解困难");
        }

        return String.join("；", reasons);
    }

    private String buildSuggestion(int level) {

        return switch (level) {
            case 0 ->
                    "优先复习对应课程资料，完成基础练习，并针对错题进行重练。";

            case 1 ->
                    "建议增加该知识点的练习次数，重点检查错误题目和重复提问的问题。";

            default ->
                    "当前没有明显薄弱信号，可以继续按照学习路径学习并保持练习。";
        };
    }

    private record PracticePoint(
            Long pointId,
            String title,
            int practiceQuestionCount,
            int masteredQuestionCount,
            Integer practiceMasteryRate,
            int selfAssessmentLevel,
            LocalDateTime lastPracticedAt
    ) {}
}
