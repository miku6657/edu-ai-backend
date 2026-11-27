package com.edu.ai.exercise.service;

import com.edu.ai.course.service.CoursePermissionService;
import com.edu.ai.exercise.dto.StudentExerciseRecommendationVO;
import com.edu.ai.exercise.dto.StudentExerciseRecommendationVO.KnowledgePointMatch;
import com.edu.ai.exercise.dto.StudentExerciseRecommendationVO.Recommendation;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class StudentExerciseRecommendationService {

    private final JdbcTemplate jdbc;
    private final CoursePermissionService permissions;

    private static final int MAX_RECOMMENDATIONS = 8;

    public StudentExerciseRecommendationService(
            JdbcTemplate jdbc,
            CoursePermissionService permissions) {

        this.jdbc = jdbc;
        this.permissions = permissions;
    }

    /**
     * 获取学生个性化练习推荐。
     *
     * 规则：
     * 1. 同一道题只统计最近一次完成的答题结果。
     * 2. 原始练习和错题重练统一计算。
     * 3. 重练时间更新时覆盖原始练习结果。
     * 4. 未完成批改和自我核对的数据不参与统计。
     * 5. 根据掌握率确定目标练习难度。
     * 6. 只推荐学生已加入课程中已发布的练习。
     */
    public StudentExerciseRecommendationVO recommend(
            Long studentId,
            Long courseId) {

        if (courseId != null) {
            permissions.requireActiveRead(courseId, studentId);
        }

        // 1. 获取学生各知识点掌握情况
        List<PointState> pointStates =
                loadKnowledgePointStates(studentId, courseId);

        if (pointStates.isEmpty()) {
            return new StudentExerciseRecommendationVO(
                    0,
                    0,
                    List.of()
            );
        }

        Map<PointKey, PointState> pointMap = new HashMap<>();

        for (PointState point : pointStates) {
            pointMap.put(
                    new PointKey(
                            point.courseId(),
                            point.knowledgePointId()
                    ),
                    point
            );
        }

        // 2. 查询已发布练习及其知识点
        List<ExercisePointRow> exerciseRows =
                loadExercisePoints(studentId, courseId);

        Map<Long, ExerciseCandidate> candidates =
                new LinkedHashMap<>();

        for (ExercisePointRow row : exerciseRows) {

            PointState point = pointMap.get(
                    new PointKey(
                            row.courseId(),
                            row.knowledgePointId()
                    )
            );

            if (point == null) {
                continue;
            }

            ExerciseCandidate candidate = candidates.computeIfAbsent(
                    row.exerciseId(),
                    id -> new ExerciseCandidate(
                            row.exerciseId(),
                            row.courseId(),
                            row.courseName(),
                            row.title(),
                            row.description(),
                            row.difficulty(),
                            row.estimatedMinutes(),
                            row.questionCount()
                    )
            );

            candidate.addPoint(point);
        }

        // 3. 计算推荐得分
        List<ExerciseCandidate> ranked =
                new ArrayList<>(candidates.values());

        ranked.removeIf(
                candidate -> candidate.points.isEmpty()
        );

        for (ExerciseCandidate candidate : ranked) {
            candidate.relevanceScore = calculateScore(candidate);
        }

        // 4. 优先推荐薄弱知识点匹配度高的练习
        ranked.sort(
                Comparator
                        .comparingInt(
                                (ExerciseCandidate item) ->
                                        item.relevanceScore
                        )
                        .reversed()
                        .thenComparingInt(
                                item -> item.estimatedMinutes
                        )
                        .thenComparingLong(
                                item -> item.exerciseId
                        )
        );

        // 5. 生成推荐结果
        List<Recommendation> recommendations =
                ranked.stream()
                        .limit(MAX_RECOMMENDATIONS)
                        .map(this::toRecommendation)
                        .toList();

        int weakCount = (int) pointStates.stream()
                .filter(point ->
                        !point.hasPracticeRecord()
                                || point.masteryRate() < 85
                )
                .count();

        return new StudentExerciseRecommendationVO(
                weakCount,
                recommendations.size(),
                recommendations
        );
    }

    /**
     * 获取学生各课程知识点掌握情况。
     *
     * 原始练习结果与重练结果合并，
     * 使用 ROW_NUMBER 选出每道题最近一次完成记录。
     */
    private List<PointState> loadKnowledgePointStates(
            Long studentId,
            Long courseId) {

        StringBuilder sql = new StringBuilder(
                """
                WITH all_practice AS (

                    -- 原始练习的已完成答题记录
                    SELECT
                        c.id AS course_id,
                        c.name AS course_name,

                        kp.id AS knowledge_point_id,
                        kp.title AS knowledge_point_title,

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

                    JOIN course_exercise e
                      ON e.id = a.exercise_id

                    JOIN course c
                      ON c.id = a.course_id

                    JOIN course_knowledge_point kp
                      ON kp.id = q.knowledge_point_id
                     AND kp.course_id = c.id

                    WHERE a.student_id = ?
                      AND a.status = 'GRADED'
                      AND aa.pending_review = 0
                      AND aa.is_correct IS NOT NULL
                      AND c.status = 1

                    UNION ALL

                    -- 错题重练的已完成答题记录
                    SELECT
                        c.id AS course_id,
                        c.name AS course_name,

                        kp.id AS knowledge_point_id,
                        kp.title AS knowledge_point_title,

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
                      AND a.status = 'GRADED'
                      AND c.status = 1

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
                    c.id AS course_id,
                    c.name AS course_name,

                    kp.id AS knowledge_point_id,
                    kp.title AS knowledge_point_title,

                    kp.difficulty AS point_difficulty,

                    COALESCE(m.question_count, 0)
                        AS question_count,

                    COALESCE(m.mastered_count, 0)
                        AS mastered_count,

                    m.mastery_rate,

                    CASE
                        WHEN m.question_count IS NULL THEN 0
                        ELSE 1
                    END AS has_practice_record,

                    m.last_practiced_at

                FROM course_student cs

                JOIN course c
                  ON c.id = cs.course_id

                JOIN course_knowledge_point kp
                  ON kp.course_id = c.id

                LEFT JOIN mastery m
                  ON m.course_id = c.id
                 AND m.knowledge_point_id = kp.id

                WHERE cs.student_id = ?
                  AND c.status = 1

                  AND EXISTS (
                      SELECT 1
                      FROM course_exercise e
                      JOIN course_exercise_question q
                        ON q.exercise_id = e.id
                      WHERE e.course_id = c.id
                        AND e.status = 'PUBLISHED'
                        AND q.knowledge_point_id = kp.id
                  )
                """
        );

        List<Object> args = new ArrayList<>();

        args.add(studentId);
        args.add(studentId);
        args.add(studentId);

        if (courseId != null) {
            sql.append(" AND c.id = ? ");
            args.add(courseId);
        }

        sql.append("""
                ORDER BY
                    c.id,
                    kp.sort_order,
                    kp.id
                """);

        return jdbc.query(
                sql.toString(),
                (rs, rowNum) -> {

                    boolean hasRecord =
                            rs.getInt("has_practice_record") == 1;

                    int rateValue = rs.getInt("mastery_rate");

                    Integer rate = rs.wasNull()
                            ? null
                            : rateValue;

                    int targetDifficulty =
                            calculateTargetDifficulty(
                                    hasRecord,
                                    rate
                            );

                    String status = !hasRecord
                            ? "NO_DATA"
                            : rate < 85
                                    ? "NEEDS_PRACTICE"
                                    : "MASTERED";

                    String reason = buildPointReason(
                            hasRecord,
                            rate,
                            targetDifficulty
                    );

                    return new PointState(
                            rs.getLong("course_id"),
                            rs.getString("course_name"),
                            rs.getLong("knowledge_point_id"),
                            rs.getString("knowledge_point_title"),
                            rs.getInt("point_difficulty"),
                            rs.getInt("question_count"),
                            rs.getInt("mastered_count"),
                            rate,
                            hasRecord,
                            toLocalDateTime(
                                    rs.getTimestamp("last_practiced_at")
                            ),
                            targetDifficulty,
                            status,
                            reason
                    );
                },
                args.toArray()
        );
    }

    /**
     * 查询学生已加入课程中的已发布练习。
     *
     * 每份练习可能包含多个知识点，
     * 因此每份练习会返回多条知识点关联记录。
     */
    private List<ExercisePointRow> loadExercisePoints(
            Long studentId,
            Long courseId) {

        StringBuilder sql = new StringBuilder(
                """
                SELECT
                    e.id AS exercise_id,
                    e.course_id,
                    c.name AS course_name,

                    e.title,
                    e.description,
                    e.difficulty,
                    e.estimated_minutes,

                    (
                        SELECT COUNT(*)
                        FROM course_exercise_question qc
                        WHERE qc.exercise_id = e.id
                    ) AS question_count,

                    kp.id AS knowledge_point_id,
                    kp.title AS knowledge_point_title

                FROM course_exercise e

                JOIN course c
                  ON c.id = e.course_id

                JOIN course_student cs
                  ON cs.course_id = e.course_id
                 AND cs.student_id = ?

                JOIN course_exercise_question q
                  ON q.exercise_id = e.id

                JOIN course_knowledge_point kp
                  ON kp.id = q.knowledge_point_id
                 AND kp.course_id = e.course_id

                WHERE e.status = 'PUBLISHED'
                  AND c.status = 1
                """
        );

        List<Object> args = new ArrayList<>();
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
                    e.estimated_minutes,
                    kp.id,
                    kp.title

                ORDER BY
                    e.published_at DESC,
                    e.id DESC
                """);

        return jdbc.query(
                sql.toString(),
                (rs, rowNum) -> new ExercisePointRow(
                        rs.getLong("exercise_id"),
                        rs.getLong("course_id"),
                        rs.getString("course_name"),
                        rs.getString("title"),
                        rs.getString("description"),
                        rs.getInt("difficulty"),
                        rs.getInt("estimated_minutes"),
                        rs.getInt("question_count"),
                        rs.getLong("knowledge_point_id"),
                        rs.getString("knowledge_point_title")
                ),
                args.toArray()
        );
    }

    /**
     * 目标难度：
     * 无记录：基础
     * 0-59：基础
     * 60-84：中等
     * 85-100：进阶
     */
    private int calculateTargetDifficulty(
            boolean hasRecord,
            Integer masteryRate) {

        if (!hasRecord || masteryRate == null) {
            return 1;
        }

        if (masteryRate < 60) {
            return 1;
        }

        if (masteryRate < 85) {
            return 2;
        }

        return 3;
    }

    private String buildPointReason(
            boolean hasRecord,
            Integer masteryRate,
            int targetDifficulty) {

        if (!hasRecord) {
            return "你还没有该知识点的练习记录，建议先从基础练习开始。";
        }

        if (masteryRate < 60) {
            return "最近掌握率为 " + masteryRate
                    + "% ，属于当前薄弱知识点，建议先巩固基础。";
        }

        if (masteryRate < 85) {
            return "最近掌握率为 " + masteryRate
                    + "% ，已有一定基础，建议通过中等难度练习巩固。";
        }

        return "最近掌握率为 " + masteryRate
                + "% ，基础掌握较好，推荐进阶练习拓展。";
    }

    /**
     * 计算练习匹配分。
     *
     * 掌握率越低，知识点权重越高；
     * 练习难度越接近目标难度，匹配分越高。
     */
    private int calculateScore(
            ExerciseCandidate candidate) {

        int total = 0;

        for (PointState point : candidate.points) {

            int weakness = point.hasPracticeRecord()
                    ? 100 - point.masteryRate()
                    : 60;

            int distance = Math.abs(
                    candidate.difficulty
                            - point.targetDifficulty()
            );

            int difficultyFit = Math.max(
                    0,
                    30 - distance * 15
            );

            total += weakness + difficultyFit;
        }

        return total / candidate.points.size();
    }

    private Recommendation toRecommendation(
            ExerciseCandidate candidate) {

        candidate.points.sort(
                Comparator.comparingInt(
                        (PointState point) ->
                                point.hasPracticeRecord()
                                        ? 100 - point.masteryRate()
                                        : 60
                ).reversed()
        );

        List<KnowledgePointMatch> matches =
                candidate.points.stream()
                        .map(point -> new KnowledgePointMatch(
                                String.valueOf(point.knowledgePointId()),
                                point.title(),
                                point.masteryRate(),
                                point.masteryStatus(),
                                point.targetDifficulty(),
                                point.recommendationReason()
                        ))
                        .toList();

        String reason = candidate.points.stream()
                .limit(2)
                .map(PointState::recommendationReason)
                .reduce((a, b) -> a + "；" + b)
                .orElse("该练习覆盖当前课程的学习知识点。");

        if (candidate.points.size() > 1) {
            reason += " 本练习同时覆盖多个相关知识点。";
        }

        return new Recommendation(
                String.valueOf(candidate.exerciseId),
                String.valueOf(candidate.courseId),
                candidate.courseName,
                candidate.title,
                candidate.description,
                candidate.difficulty,
                candidate.estimatedMinutes,
                candidate.questionCount,
                candidate.relevanceScore,
                difficultyLabel(candidate.difficulty),
                reason,
                matches
        );
    }

    private String difficultyLabel(int difficulty) {
        return switch (difficulty) {
            case 1 -> "基础巩固";
            case 2 -> "强化练习";
            case 3 -> "进阶提升";
            default -> "综合练习";
        };
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null
                ? null
                : timestamp.toLocalDateTime();
    }

    private record PointKey(
            Long courseId,
            Long knowledgePointId
    ) {}

    private record PointState(
            Long courseId,
            String courseName,
            Long knowledgePointId,
            String title,
            Integer pointDifficulty,
            Integer questionCount,
            Integer masteredCount,
            Integer masteryRate,
            boolean hasPracticeRecord,
            LocalDateTime lastPracticedAt,
            Integer targetDifficulty,
            String masteryStatus,
            String recommendationReason
    ) {}

    private record ExercisePointRow(
            Long exerciseId,
            Long courseId,
            String courseName,
            String title,
            String description,
            Integer difficulty,
            Integer estimatedMinutes,
            Integer questionCount,
            Long knowledgePointId,
            String knowledgePointTitle
    ) {}

    private static class ExerciseCandidate {

        private final Long exerciseId;
        private final Long courseId;
        private final String courseName;
        private final String title;
        private final String description;
        private final Integer difficulty;
        private final Integer estimatedMinutes;
        private final Integer questionCount;

        private int relevanceScore;

        private final Map<Long, PointState> points =
                new LinkedHashMap<>();

        private ExerciseCandidate(
                Long exerciseId,
                Long courseId,
                String courseName,
                String title,
                String description,
                Integer difficulty,
                Integer estimatedMinutes,
                Integer questionCount) {

            this.exerciseId = exerciseId;
            this.courseId = courseId;
            this.courseName = courseName;
            this.title = title;
            this.description = description;
            this.difficulty = difficulty;
            this.estimatedMinutes = estimatedMinutes;
            this.questionCount = questionCount;
        }

        private void addPoint(PointState point) {
            points.putIfAbsent(
                    point.knowledgePointId(),
                    point
            );
        }
    }
}
