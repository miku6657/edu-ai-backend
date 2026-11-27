package com.edu.ai.learning.service;

import com.edu.ai.learning.dto.StudentLearningAnalysisVO;
import com.edu.ai.learning.dto.StudentLearningAnalysisVO.DailyRecord;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class StudentLearningAnalysisService {

    private static final ZoneId APP_ZONE =
            ZoneId.of("Asia/Shanghai");

    private final JdbcTemplate jdbc;

    public StudentLearningAnalysisService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 查询学生学习概况和本周每日学习记录。
     */
    public StudentLearningAnalysisVO getOverview(Long studentId) {

        ExerciseSummary exerciseSummary =
                queryExerciseSummary(studentId);

        long questionCount =
                queryQuestionCount(studentId);

        TaskSummary taskSummary =
                queryTaskSummary(studentId);

        LocalDate today = LocalDate.now(APP_ZONE);

        // 本周从星期一开始
        LocalDate weekStart = today.with(
                TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)
        );

        LocalDate weekEnd = weekStart.plusDays(6);

        LocalDateTime startAt = weekStart.atStartOfDay();
        LocalDateTime endAt = weekStart.plusWeeks(1).atStartOfDay();

        // 每日练习次数
        Map<LocalDate, Long> dailyExercises =
                queryDailyCounts(
                        """
                        SELECT
                            DATE(submitted_at) AS activity_date,
                            COUNT(*) AS activity_count
                        FROM course_exercise_attempt
                        WHERE student_id = ?
                          AND status IN ('GRADED', 'PENDING_REVIEW')
                          AND submitted_at >= ?
                          AND submitted_at < ?
                        GROUP BY DATE(submitted_at)
                        """,
                        studentId,
                        startAt,
                        endAt
                );

        // 每日课程提问次数
        Map<LocalDate, Long> dailyQuestions =
                queryDailyCounts(
                        """
                        SELECT
                            DATE(m.create_time) AS activity_date,
                            COUNT(DISTINCT m.id) AS activity_count
                        FROM chat_message m
                        JOIN chat_session s
                          ON s.id = m.session_id
                        WHERE s.user_id = ?
                          AND s.course_id IS NOT NULL
                          AND m.role = 'user'
                          AND m.create_time >= ?
                          AND m.create_time < ?
                          AND NOT EXISTS (
                              SELECT 1
                              FROM course_exercise_explain_session ees
                              WHERE ees.chat_session_id = s.id
                          )
                        GROUP BY DATE(m.create_time)
                        """,
                        studentId,
                        startAt,
                        endAt
                );

        // 每日完成任务数量
        Map<LocalDate, Long> dailyCompletedTasks =
                queryDailyCounts(
                        """
                        SELECT
                            DATE(ts.completed_time) AS activity_date,
                            COUNT(DISTINCT ts.task_id) AS activity_count
                        FROM task_student ts
                        WHERE ts.student_id = ?
                          AND ts.completed = 1
                          AND ts.completed_time IS NOT NULL
                          AND ts.completed_time >= ?
                          AND ts.completed_time < ?
                        GROUP BY DATE(ts.completed_time)
                        """,
                        studentId,
                        startAt,
                        endAt
                );

        // 固定生成星期一至星期日七条记录
        List<DailyRecord> weeklyRecords =
                new ArrayList<>(7);

        for (int i = 0; i < 7; i++) {

            LocalDate date = weekStart.plusDays(i);

            weeklyRecords.add(
                    new DailyRecord(
                            date,
                            dailyExercises.getOrDefault(date, 0L),
                            dailyQuestions.getOrDefault(date, 0L),
                            dailyCompletedTasks.getOrDefault(date, 0L)
                    )
            );
        }

        return new StudentLearningAnalysisVO(
                exerciseSummary.exerciseCount(),
                exerciseSummary.accuracyRate(),
                exerciseSummary.objectiveAnswerCount(),
                questionCount,
                taskSummary.completedTaskCount(),
                taskSummary.totalTaskCount(),
                weekStart,
                weekEnd,
                weeklyRecords
        );
    }

    /**
     * 练习次数统计已提交的答题记录。
     *
     * 正确率只统计已经判分的客观题，
     * 简答题不计入客观题正确率。
     */
    private ExerciseSummary queryExerciseSummary(
            Long studentId) {

        return jdbc.queryForObject(
                """
                SELECT
                    COUNT(DISTINCT a.id) AS exercise_count,

                    COUNT(q.id) AS objective_answer_count,

                    COALESCE(
                        SUM(
                            CASE
                                WHEN q.id IS NOT NULL
                                 AND aa.is_correct = 1 THEN 1
                                ELSE 0
                            END
                        ),
                        0
                    ) AS correct_answer_count

                FROM course_exercise_attempt a

                LEFT JOIN course_exercise_attempt_answer aa
                  ON aa.attempt_id = a.id
                 AND aa.is_correct IS NOT NULL

                LEFT JOIN course_exercise_question q
                  ON q.id = aa.question_id
                 AND q.question_type <> 'SHORT_ANSWER'

                WHERE a.student_id = ?
                  AND a.status IN ('GRADED', 'PENDING_REVIEW')
                """,
                (rs, rowNum) -> {

                    long exerciseCount =
                            rs.getLong("exercise_count");

                    long objectiveAnswerCount =
                            rs.getLong("objective_answer_count");

                    long correctAnswerCount =
                            rs.getLong("correct_answer_count");

                    Integer accuracyRate =
                            objectiveAnswerCount == 0
                                    ? null
                                    : (int) Math.round(
                                            correctAnswerCount
                                                    * 100.0
                                                    / objectiveAnswerCount
                                    );

                    return new ExerciseSummary(
                            exerciseCount,
                            objectiveAnswerCount,
                            accuracyRate
                    );
                },
                studentId
        );
    }

    /**
     * 统计学生发起的普通课程提问。
     *
     * 排除错题 AI 专属讲解会话。
     */
    private long queryQuestionCount(Long studentId) {

        Long count = jdbc.queryForObject(
                """
                SELECT COUNT(DISTINCT m.id)

                FROM chat_message m

                JOIN chat_session s
                  ON s.id = m.session_id

                WHERE s.user_id = ?
                  AND s.course_id IS NOT NULL
                  AND m.role = 'user'

                  AND NOT EXISTS (
                      SELECT 1
                      FROM course_exercise_explain_session ees
                      WHERE ees.chat_session_id = s.id
                  )
                """,
                Long.class,
                studentId
        );

        return count == null ? 0L : count;
    }

    /**
     * 统计当前已加入课程中的有效学习任务。
     *
     * 没有 task_student 完成记录的任务视为未完成。
     */
    private TaskSummary queryTaskSummary(Long studentId) {

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

                JOIN course c
                  ON c.id = t.course_id
                 AND c.status = 1

                JOIN course_student cs
                  ON cs.course_id = t.course_id
                 AND cs.student_id = ?

                LEFT JOIN task_student ts
                  ON ts.task_id = t.id
                 AND ts.student_id = cs.student_id

                WHERE t.status = 1
                """,
                (rs, rowNum) -> new TaskSummary(
                        rs.getLong("completed_task_count"),
                        rs.getLong("total_task_count")
                ),
                studentId
        );
    }

    /**
     * 查询指定时间范围内某类活动的每日数量。
     */
    private Map<LocalDate, Long> queryDailyCounts(
            String sql,
            Long studentId,
            LocalDateTime startAt,
            LocalDateTime endAt) {

        List<DailyCount> rows = jdbc.query(
                sql,
                (rs, rowNum) -> new DailyCount(
                        rs.getDate("activity_date").toLocalDate(),
                        rs.getLong("activity_count")
                ),
                studentId,
                Timestamp.valueOf(startAt),
                Timestamp.valueOf(endAt)
        );

        Map<LocalDate, Long> result = new HashMap<>();

        for (DailyCount row : rows) {
            result.put(row.date(), row.count());
        }

        return result;
    }

    private record ExerciseSummary(
            long exerciseCount,
            long objectiveAnswerCount,
            Integer accuracyRate
    ) {}

    private record TaskSummary(
            long completedTaskCount,
            long totalTaskCount
    ) {}

    private record DailyCount(
            LocalDate date,
            long count
    ) {}
}
