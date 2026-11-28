package com.edu.ai.audit.service;

import com.edu.ai.audit.dto.ContentOperationOverviewVO;
import com.edu.ai.audit.dto.ContentOperationOverviewVO.*;
import com.edu.ai.common.BusinessException;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ContentOperationOverviewService {

    private static final ZoneId APP_ZONE =
            ZoneId.of("Asia/Shanghai");

    private static final int MAX_PERIOD_DAYS = 366;

    private final JdbcTemplate jdbc;

    public ContentOperationOverviewService(
            JdbcTemplate jdbc) {

        this.jdbc = jdbc;
    }

    private record MaterialUsage(
            long previewCount,
            long downloadCount,
            long studentCount
    ) {}

    private record QuestionUsage(
            long questionCount,
            long studentCount
    ) {}

    private record RecommendationUsage(
            long exposureCount,
            long clickCount
    ) {}

    /**
     * 查询内容运营综合统计。
     *
     * courseId为空时查询全部课程。
     */
    @Transactional(readOnly = true)
    public ContentOperationOverviewVO getOverview(
            Long courseId,
            LocalDate startDate,
            LocalDate endDate) {

        LocalDate today = LocalDate.now(APP_ZONE);

        LocalDate end = endDate == null
                ? today
                : endDate;

        LocalDate start = startDate == null
                ? end.minusDays(29)
                : startDate;

        validatePeriod(start, end, today);

        validateCourse(courseId);

        LocalDateTime startTime =
                start.atStartOfDay();

        LocalDateTime endExclusive =
                end.plusDays(1).atStartOfDay();

        DocumentStats documents =
                queryDocumentStats(courseId, today);

        QualityStats quality =
                queryQualityStats(courseId);

        HotQuestionStats hotQuestions =
                queryHotQuestionStats(courseId);

        MaterialUsage materials =
                queryMaterialUsage(
                        courseId,
                        startTime,
                        endExclusive
                );

        QuestionUsage questions =
                queryQuestionUsage(
                        courseId,
                        startTime,
                        endExclusive
                );

        RecommendationUsage recommendations =
                queryRecommendationUsage(
                        courseId,
                        startTime,
                        endExclusive
                );

        List<OperationTypeStat> operationTypes =
                queryOperationTypes(
                        courseId,
                        startTime,
                        endExclusive
                );

        long operationCount = operationTypes.stream()
                .mapToLong(
                        OperationTypeStat::operationCount
                )
                .sum();

        UsageStats usage = new UsageStats(
                materials.previewCount(),
                materials.downloadCount(),
                materials.studentCount(),

                questions.questionCount(),
                questions.studentCount(),

                recommendations.exposureCount(),
                recommendations.clickCount(),

                percentage(
                        recommendations.clickCount(),
                        recommendations.exposureCount()
                ),

                operationCount
        );

        List<DailyUsage> dailyUsage =
                queryDailyUsage(
                        courseId,
                        start,
                        end
                );

        return new ContentOperationOverviewVO(
                courseId == null
                        ? null
                        : courseId.toString(),

                start,
                end,
                documents,
                quality,
                hotQuestions,
                usage,
                operationTypes,
                dailyUsage,
                LocalDateTime.now(APP_ZONE)
        );
    }

    // =====================================================
    // 1. 课程资料状态统计
    // =====================================================

    private DocumentStats queryDocumentStats(
            Long courseId,
            LocalDate today) {

        String sql = """
                SELECT

                    COUNT(*) AS published_count,

                    COUNT(
                        CASE
                            WHEN d.valid_until >= ?

                             AND DATEDIFF(
                                 d.valid_until,
                                 ?
                             ) <= COALESCE(
                                 d.expiry_remind_days,
                                 7
                             )

                            THEN 1
                        END
                    ) AS expiring_count,

                    COUNT(
                        CASE
                            WHEN d.valid_until < ?
                            THEN 1
                        END
                    ) AS expired_count,

                    COUNT(
                        CASE
                            WHEN d.quality_status = 'SUSPENDED'
                            THEN 1
                        END
                    ) AS suspended_count,

                    COUNT(
                        CASE
                            WHEN EXISTS (
                                SELECT 1
                                FROM course_document_summary s

                                WHERE s.document_id = d.id

                                  AND s.course_id = d.course_id

                                  AND s.status = 'CONFIRMED'
                            )
                            THEN 1
                        END
                    ) AS confirmed_summary_count

                FROM course_document d

                WHERE d.status = 'READY'

                  AND d.version_status = 'CURRENT'

                  AND d.review_status = 'APPROVED'
                """;

        List<Object> args = new ArrayList<>();

        args.add(today);
        args.add(today);
        args.add(today);

        if (courseId != null) {

            sql += " AND d.course_id = ? ";

            args.add(courseId);
        }

        return jdbc.queryForObject(
                sql,
                (rs, rowNum) -> new DocumentStats(
                        rs.getLong("published_count"),
                        rs.getLong("expiring_count"),
                        rs.getLong("expired_count"),
                        rs.getLong("suspended_count"),
                        rs.getLong("confirmed_summary_count")
                ),
                args.toArray()
        );
    }

    // =====================================================
    // 2. 资料质量问题统计
    // =====================================================

    private QualityStats queryQualityStats(
            Long courseId) {

        String sql = """
                SELECT

                    COUNT(
                        CASE
                            WHEN i.status = 'OPEN'
                            THEN 1
                        END
                    ) AS open_issue_count,

                    COUNT(
                        CASE
                            WHEN i.status = 'RESOLVED'
                            THEN 1
                        END
                    ) AS resolved_issue_count

                FROM course_document_quality_issue i

                JOIN course_document_quality_task t
                  ON t.id = i.task_id

                WHERE t.status = 'IN_PROGRESS'
                """;

        List<Object> args = new ArrayList<>();

        if (courseId != null) {

            sql += " AND t.course_id = ? ";

            args.add(courseId);
        }

        return jdbc.queryForObject(
                sql,
                (rs, rowNum) -> new QualityStats(
                        rs.getLong("open_issue_count"),
                        rs.getLong("resolved_issue_count")
                ),
                args.toArray()
        );
    }

    // =====================================================
    // 3. 热门标准问答统计
    // =====================================================

    private HotQuestionStats queryHotQuestionStats(
            Long courseId) {

        String sql = """
                SELECT

                    COUNT(
                        CASE WHEN status = 'DRAFT'
                        THEN 1 END
                    ) AS draft_count,

                    COUNT(
                        CASE WHEN status = 'PENDING_REVIEW'
                        THEN 1 END
                    ) AS pending_review_count,

                    COUNT(
                        CASE WHEN status = 'PUBLISHED'
                        THEN 1 END
                    ) AS published_count,

                    COUNT(
                        CASE WHEN status = 'REJECTED'
                        THEN 1 END
                    ) AS rejected_count,

                    COUNT(
                        CASE WHEN status = 'OFFLINE'
                        THEN 1 END
                    ) AS offline_count

                FROM course_hot_question

                WHERE 1 = 1
                """;

        List<Object> args = new ArrayList<>();

        if (courseId != null) {

            sql += " AND course_id = ? ";

            args.add(courseId);
        }

        return jdbc.queryForObject(
                sql,
                (rs, rowNum) -> new HotQuestionStats(
                        rs.getLong("draft_count"),
                        rs.getLong("pending_review_count"),
                        rs.getLong("published_count"),
                        rs.getLong("rejected_count"),
                        rs.getLong("offline_count")
                ),
                args.toArray()
        );
    }

    // =====================================================
    // 4. 资料访问统计
    // =====================================================

    private MaterialUsage queryMaterialUsage(
            Long courseId,
            LocalDateTime start,
            LocalDateTime endExclusive) {

        String sql = """
                SELECT

                    COUNT(
                        CASE
                            WHEN access_type = 'PREVIEW'
                            THEN 1
                        END
                    ) AS preview_count,

                    COUNT(
                        CASE
                            WHEN access_type = 'DOWNLOAD'
                            THEN 1
                        END
                    ) AS download_count,

                    COUNT(DISTINCT student_id)
                        AS student_count

                FROM course_document_access_log

                WHERE accessed_at >= ?

                  AND accessed_at < ?
                """;

        List<Object> args =
                periodArgs(start, endExclusive);

        if (courseId != null) {

            sql += " AND course_id = ? ";

            args.add(courseId);
        }

        return jdbc.queryForObject(
                sql,
                (rs, rowNum) -> new MaterialUsage(
                        rs.getLong("preview_count"),
                        rs.getLong("download_count"),
                        rs.getLong("student_count")
                ),
                args.toArray()
        );
    }

    // =====================================================
    // 5. 学生课程问答统计
    // =====================================================

    private QuestionUsage queryQuestionUsage(
            Long courseId,
            LocalDateTime start,
            LocalDateTime endExclusive) {

        String sql = """
                SELECT

                    COUNT(DISTINCT m.id)
                        AS question_count,

                    COUNT(DISTINCT s.user_id)
                        AS student_count

                FROM chat_message m

                JOIN chat_session s
                  ON s.id = m.session_id

                JOIN sys_user u
                  ON u.id = s.user_id

                WHERE m.role = 'user'

                  AND u.role = 'STUDENT'

                  AND s.course_id IS NOT NULL

                  AND m.create_time >= ?

                  AND m.create_time < ?

                  AND NOT EXISTS (
                      SELECT 1

                      FROM course_exercise_explain_session ees

                      WHERE ees.chat_session_id = s.id
                  )
                """;

        List<Object> args =
                periodArgs(start, endExclusive);

        if (courseId != null) {

            sql += " AND s.course_id = ? ";

            args.add(courseId);
        }

        return jdbc.queryForObject(
                sql,
                (rs, rowNum) -> new QuestionUsage(
                        rs.getLong("question_count"),
                        rs.getLong("student_count")
                ),
                args.toArray()
        );
    }

    // =====================================================
    // 6. 练习推荐使用统计
    // =====================================================

    private RecommendationUsage queryRecommendationUsage(
            Long courseId,
            LocalDateTime start,
            LocalDateTime endExclusive) {

        /*
         * 以曝光时间确定统计批次。
         *
         * 只统计这些曝光记录在统计截止时间之前
         * 对应的点击，避免点击率超过100%。
         */
        String sql = """
                SELECT

                    COUNT(*) AS exposure_count,

                    COUNT(click.id) AS click_count

                FROM course_exercise_recommendation_event exposure

                LEFT JOIN course_exercise_recommendation_event click

                  ON click.recommendation_batch_id =
                     exposure.recommendation_batch_id

                 AND click.student_id = exposure.student_id

                 AND click.exercise_id = exposure.exercise_id

                 AND click.course_id = exposure.course_id

                 AND click.event_type = 'CLICK'

                 AND click.event_at >= exposure.event_at

                 AND click.event_at < ?

                WHERE exposure.event_type = 'EXPOSURE'

                  AND exposure.event_at >= ?

                  AND exposure.event_at < ?
                """;

        List<Object> args = new ArrayList<>();

        args.add(Timestamp.valueOf(endExclusive));

        args.add(Timestamp.valueOf(start));

        args.add(Timestamp.valueOf(endExclusive));

        if (courseId != null) {

            sql += " AND exposure.course_id = ? ";

            args.add(courseId);
        }

        return jdbc.queryForObject(
                sql,
                (rs, rowNum) -> new RecommendationUsage(
                        rs.getLong("exposure_count"),
                        rs.getLong("click_count")
                ),
                args.toArray()
        );
    }

    // =====================================================
    // 7. 运营日志类型分布
    // =====================================================

    private List<OperationTypeStat> queryOperationTypes(
            Long courseId,
            LocalDateTime start,
            LocalDateTime endExclusive) {

        String sql = """
                SELECT

                    operation_type,

                    MAX(operation_name) AS operation_name,

                    COUNT(*) AS operation_count

                FROM course_content_operation_log

                WHERE create_time >= ?

                  AND create_time < ?
                """;

        List<Object> args =
                periodArgs(start, endExclusive);

        if (courseId != null) {

            sql += " AND course_id = ? ";

            args.add(courseId);
        }

        sql += """
                GROUP BY operation_type

                ORDER BY
                    operation_count DESC,
                    operation_type ASC
                """;

        return jdbc.query(
                sql,
                (rs, rowNum) -> new OperationTypeStat(
                        rs.getString("operation_type"),
                        rs.getString("operation_name"),
                        rs.getLong("operation_count")
                ),
                args.toArray()
        );
    }

    // =====================================================
    // 8. 每日使用趋势
    // =====================================================

    private List<DailyUsage> queryDailyUsage(
            Long courseId,
            LocalDate start,
            LocalDate end) {

        LocalDateTime startTime =
                start.atStartOfDay();

        LocalDateTime endExclusive =
                end.plusDays(1).atStartOfDay();

        /*
         * 不直接连接多张行为表。
         *
         * 各业务独立按天聚合后，
         * 在Java中合并，避免多表关联造成重复计数。
         */
        Map<LocalDate, long[]> daily =
                new HashMap<>();

        String materialSql = """
                SELECT

                    DATE(accessed_at) AS stat_date,

                    COUNT(
                        CASE WHEN access_type = 'PREVIEW'
                        THEN 1 END
                    ) AS previews,

                    COUNT(
                        CASE WHEN access_type = 'DOWNLOAD'
                        THEN 1 END
                    ) AS downloads

                FROM course_document_access_log

                WHERE accessed_at >= ?

                  AND accessed_at < ?
                """;

        List<Object> materialArgs =
                periodArgs(startTime, endExclusive);

        if (courseId != null) {

            materialSql += " AND course_id = ? ";

            materialArgs.add(courseId);
        }

        materialSql += " GROUP BY DATE(accessed_at) ";

        jdbc.query(
                materialSql,
                rs -> {

                    LocalDate date = rs.getObject(
                            "stat_date",
                            LocalDate.class
                    );

                    long[] values =
                            daily.computeIfAbsent(
                                    date,
                                    key -> new long[6]
                            );

                    values[0] = rs.getLong("previews");

                    values[1] = rs.getLong("downloads");
                },
                materialArgs.toArray()
        );

        String questionSql = """
                SELECT

                    DATE(m.create_time) AS stat_date,

                    COUNT(DISTINCT m.id) AS questions

                FROM chat_message m

                JOIN chat_session s
                  ON s.id = m.session_id

                JOIN sys_user u
                  ON u.id = s.user_id

                WHERE m.role = 'user'

                  AND u.role = 'STUDENT'

                  AND s.course_id IS NOT NULL

                  AND m.create_time >= ?

                  AND m.create_time < ?

                  AND NOT EXISTS (
                      SELECT 1

                      FROM course_exercise_explain_session ees

                      WHERE ees.chat_session_id = s.id
                  )
                """;

        List<Object> questionArgs =
                periodArgs(startTime, endExclusive);

        if (courseId != null) {

            questionSql += " AND s.course_id = ? ";

            questionArgs.add(courseId);
        }

        questionSql += " GROUP BY DATE(m.create_time) ";

        jdbc.query(
                questionSql,
                rs -> {

                    LocalDate date = rs.getObject(
                            "stat_date",
                            LocalDate.class
                    );

                    long[] values =
                            daily.computeIfAbsent(
                                    date,
                                    key -> new long[6]
                            );

                    values[2] = rs.getLong("questions");
                },
                questionArgs.toArray()
        );

        String recommendationSql = """
                SELECT

                    DATE(exposure.event_at) AS stat_date,

                    COUNT(*) AS exposures,

                    COUNT(click.id) AS clicks

                FROM course_exercise_recommendation_event exposure

                LEFT JOIN course_exercise_recommendation_event click

                  ON click.recommendation_batch_id =
                     exposure.recommendation_batch_id

                 AND click.student_id = exposure.student_id

                 AND click.exercise_id = exposure.exercise_id

                 AND click.course_id = exposure.course_id

                 AND click.event_type = 'CLICK'

                 AND click.event_at >= exposure.event_at

                 AND click.event_at < ?

                WHERE exposure.event_type = 'EXPOSURE'

                  AND exposure.event_at >= ?

                  AND exposure.event_at < ?
                """;

        List<Object> recommendationArgs =
                new ArrayList<>();

        recommendationArgs.add(
                Timestamp.valueOf(endExclusive)
        );

        recommendationArgs.add(
                Timestamp.valueOf(startTime)
        );

        recommendationArgs.add(
                Timestamp.valueOf(endExclusive)
        );

        if (courseId != null) {

            recommendationSql +=
                    " AND exposure.course_id = ? ";

            recommendationArgs.add(courseId);
        }

        recommendationSql +=
                " GROUP BY DATE(exposure.event_at) ";

        jdbc.query(
                recommendationSql,
                rs -> {

                    LocalDate date = rs.getObject(
                            "stat_date",
                            LocalDate.class
                    );

                    long[] values =
                            daily.computeIfAbsent(
                                    date,
                                    key -> new long[6]
                            );

                    values[3] = rs.getLong("exposures");

                    values[4] = rs.getLong("clicks");
                },
                recommendationArgs.toArray()
        );

        String operationSql = """
                SELECT

                    DATE(create_time) AS stat_date,

                    COUNT(*) AS operations

                FROM course_content_operation_log

                WHERE create_time >= ?

                  AND create_time < ?
                """;

        List<Object> operationArgs =
                periodArgs(startTime, endExclusive);

        if (courseId != null) {

            operationSql += " AND course_id = ? ";

            operationArgs.add(courseId);
        }

        operationSql += " GROUP BY DATE(create_time) ";

        jdbc.query(
                operationSql,
                rs -> {

                    LocalDate date = rs.getObject(
                            "stat_date",
                            LocalDate.class
                    );

                    long[] values =
                            daily.computeIfAbsent(
                                    date,
                                    key -> new long[6]
                            );

                    values[5] =
                            rs.getLong("operations");
                },
                operationArgs.toArray()
        );

        // 没有学习行为的日期补0。
        List<DailyUsage> result =
                new ArrayList<>();

        for (
                LocalDate date = start;

                !date.isAfter(end);

                date = date.plusDays(1)
        ) {

            long[] values = daily.getOrDefault(
                    date,
                    new long[6]
            );

            result.add(
                    new DailyUsage(
                            date,
                            values[0],
                            values[1],
                            values[2],
                            values[3],
                            values[4],
                            values[5]
                    )
            );
        }

        return result;
    }

    // =====================================================
    // 9. 参数与公共方法
    // =====================================================

    private void validatePeriod(
            LocalDate start,
            LocalDate end,
            LocalDate today) {

        if (start.isAfter(end)) {

            throw new BusinessException(
                    400,
                    "开始日期不能晚于结束日期"
            );
        }

        if (end.isAfter(today)) {

            throw new BusinessException(
                    400,
                    "结束日期不能晚于今天"
            );
        }

        long days =
                ChronoUnit.DAYS.between(start, end) + 1;

        if (days > MAX_PERIOD_DAYS) {

            throw new BusinessException(
                    400,
                    "统计时间范围不能超过366天"
            );
        }
    }

    private void validateCourse(Long courseId) {

        if (courseId == null) {
            return;
        }

        Long count = jdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM course
                WHERE id = ?
                """,
                Long.class,
                courseId
        );

        if (count == null || count != 1) {

            throw new BusinessException(
                    404,
                    "课程不存在"
            );
        }
    }

    private List<Object> periodArgs(
            LocalDateTime start,
            LocalDateTime endExclusive) {

        List<Object> args = new ArrayList<>();

        args.add(Timestamp.valueOf(start));

        args.add(Timestamp.valueOf(endExclusive));

        return args;
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
}
