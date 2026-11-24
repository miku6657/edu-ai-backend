package com.edu.ai.notification.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.edu.ai.document.entity.CourseDocument;
import com.edu.ai.document.entity.CourseEmbeddingMigration;
import com.edu.ai.document.mapper.CourseDocumentMapper;
import com.edu.ai.document.mapper.CourseEmbeddingMigrationMapper;
import com.edu.ai.task.entity.DdlTask;
import com.edu.ai.task.mapper.DdlTaskMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Component
public class NotificationProducerJob {

    private static final Logger log =
            LoggerFactory.getLogger(NotificationProducerJob.class);

    private final NotificationService notifications;
    private final DdlTaskMapper ddlMapper;
    private final CourseDocumentMapper documentMapper;
    private final CourseEmbeddingMigrationMapper migrationMapper;
    private final JdbcTemplate jdbc;

    public NotificationProducerJob(
            NotificationService notifications,
            DdlTaskMapper ddlMapper,
            CourseDocumentMapper documentMapper,
            CourseEmbeddingMigrationMapper migrationMapper,
            JdbcTemplate jdbc) {

        this.notifications = notifications;
        this.ddlMapper = ddlMapper;
        this.documentMapper = documentMapper;
        this.migrationMapper = migrationMapper;
        this.jdbc = jdbc;
    }

    /**
     * 每60秒扫描DDL提醒。
     */
    @Scheduled(
            initialDelay = 15000,
            fixedDelay = 60000
    )
    public void generateDdlNotifications() {

        try {
            LocalDateTime now = LocalDateTime.now();

            List<DdlTask> tasks = ddlMapper.selectList(
                    new LambdaQueryWrapper<DdlTask>()
                            .ne(
                                    DdlTask::getStatus,
                                    "CANCELED"
                            )
                            .ne(
                                    DdlTask::getStatus,
                                    "COMPLETED"
                            )
                            .and(w -> w
                                    .between(
                                            DdlTask::getDueAt,
                                            now.minusDays(7),
                                            now.plusHours(24)
                                    )
                                    .or()
                                    .between(
                                            DdlTask::getFollowUpAt,
                                            now.minusDays(7),
                                            now
                                    )
                            )
                            .last("LIMIT 2000")
            );

            for (DdlTask task : tasks) {
                try {
                    emitTask(task, now);
                } catch (Exception e) {
                    log.warn(
                            "DDL notification failed: taskId={}",
                            task.getId(),
                            e
                    );
                }
            }

        } catch (Exception e) {
            log.error(
                    "DDL notification scan failed",
                    e
            );
        }
    }

    private void emitTask(
            DdlTask task,
            LocalDateTime now) {

        String status = task.getStatus();

        if (task.getDueAt() != null
                && ("ACTIVE".equals(status)
                    || "OVERDUE".equals(status))) {

            long minutes = Duration.between(
                    now, task.getDueAt()
            ).toMinutes();

            if (minutes > 360 && minutes <= 1440) {

                notifications.createIfAbsent(
                        task.getUserId(),
                        "DDL_T24H",
                        task.getId(),
                        "DDL 即将截止（24小时内）",
                        task.getTaskTitle()
                );

            } else if (minutes > 0 && minutes <= 360) {

                notifications.createIfAbsent(
                        task.getUserId(),
                        "DDL_T6H",
                        task.getId(),
                        "DDL 即将截止（6小时内）",
                        task.getTaskTitle()
                );

            } else if (minutes <= 0
                    && !task.getDueAt().isBefore(
                            now.minusDays(7))) {

                notifications.createIfAbsent(
                        task.getUserId(),
                        "DDL_OVERDUE",
                        task.getId(),
                        "DDL 已逾期",
                        task.getTaskTitle()
                );
            }
        }

        if (("PENDING_CONFIRM".equals(status)
                || "PENDING_TIME".equals(status))
                && task.getFollowUpAt() != null
                && !task.getFollowUpAt().isAfter(now)
                && !task.getFollowUpAt().isBefore(
                        now.minusDays(7))) {

            notifications.createIfAbsent(
                    task.getUserId(),
                    "DDL_FOLLOW_UP",
                    task.getId(),
                    "请确认 DDL 信息",
                    task.getTaskTitle()
            );
        }
    }

    /**
     * 每120秒扫描PDF入库结果。
     */
    @Scheduled(
            initialDelay = 20000,
            fixedDelay = 120000
    )
    public void generateIndexNotifications() {

        try {
            List<CourseDocument> docs =
                    documentMapper.selectList(
                            new LambdaQueryWrapper<CourseDocument>()
                                    .in(
                                            CourseDocument::getStatus,
                                            "READY",
                                            "FAILED"
                                    )
                                    .orderByDesc(
                                            CourseDocument::getId
                                    )
                                    .last("LIMIT 2000")
                    );

            for (CourseDocument doc : docs) {

                try {
                    boolean ready =
                            "READY".equals(doc.getStatus());

                    String type = ready
                            ? "DOC_INGEST_READY"
                            : "DOC_INGEST_FAILED";

                    String title = ready
                            ? "课件入库成功"
                            : "课件入库失败";

                    notifications.createIfAbsent(
                            doc.getUploaderId(),
                            type,
                            doc.getId(),
                            title,
                            doc.getOriginalName()
                    );

                } catch (Exception e) {
                    log.warn(
                            "Document notification failed: id={}",
                            doc.getId(),
                            e
                    );
                }
            }

        } catch (Exception e) {
            log.error(
                    "Document notification scan failed",
                    e
            );
        }
    }

    /**
     * 每120秒扫描课程向量索引迁移结果。
     */
    @Scheduled(
            initialDelay = 25000,
            fixedDelay = 120000
    )
    public void generateMigrationNotifications() {

        try {
            List<CourseEmbeddingMigration> jobs =
                    migrationMapper.selectList(
                            new LambdaQueryWrapper<CourseEmbeddingMigration>()
                                    .eq(
                                            CourseEmbeddingMigration::getStatus,
                                            "SUCCEEDED"
                                    )
                                    .orderByDesc(
                                            CourseEmbeddingMigration::getId
                                    )
                                    .last("LIMIT 2000")
                    );

            for (CourseEmbeddingMigration job : jobs) {

                try {
                    notifications.createIfAbsent(
                            job.getUserId(),
                            "COURSE_REBUILD_DONE",
                            job.getId(),
                            "课程知识库重建完成",
                            "课程 " + job.getCourseId()
                                    + " 的向量索引已更新至 v"
                                    + job.getTargetVersion()
                    );

                } catch (Exception e) {
                    log.warn(
                            "Migration notification failed: id={}",
                            job.getId(),
                            e
                    );
                }
            }

        } catch (Exception e) {
            log.error(
                    "Migration notification scan failed",
                    e
            );
        }
    }

    /**
     * 公共课程任务通知候选人。
     *
     * 这里不复制公共任务到学生的 ddl_task。
     * 只读取课程成员及其个人完成状态。
     */
    private record CourseTaskCandidate(
            Long taskId,
            Long studentId,
            String title,
            LocalDateTime deadline
    ) {}

    /**
     * 每分钟检查教师公共课程任务。
     *
     * 仅提醒：
     * 1. 课程处于启用状态
     * 2. 学生账号处于正常状态
     * 3. 学生仍然属于该课程
     * 4. 任务未取消且学生尚未完成
     */
    @Scheduled(
            initialDelay = 30000,
            fixedDelay = 60000
    )
    public void generateCourseTaskNotifications() {

        try {
            LocalDateTime now = LocalDateTime.now();

            generateCourseTaskPublished(now);
            generateCourseTaskDeadlines(now);

        } catch (Exception e) {
            log.error(
                    "Course task notification scan failed",
                    e
            );
        }
    }

    /**
     * 教师新发布任务时提醒学生。
     *
     * 扫描最近7天创建的公共任务，
     * 使用 sys_notification 唯一索引避免重复通知。
     */
    private void generateCourseTaskPublished(
            LocalDateTime now) {

        List<CourseTaskCandidate> candidates = jdbc.query(
                """
                SELECT
                    t.id AS task_id,
                    t.title,
                    t.deadline,
                    cs.student_id
                FROM learning_task t
                JOIN course c
                    ON c.id = t.course_id
                JOIN course_student cs
                    ON cs.course_id = t.course_id
                JOIN sys_user u
                    ON u.id = cs.student_id
                LEFT JOIN task_student ts
                    ON ts.task_id = t.id
                    AND ts.student_id = cs.student_id
                WHERE t.status = 1
                  AND c.status = 1
                  AND u.status = 1
                  AND u.role = 'STUDENT'
                  AND (ts.id IS NULL OR ts.completed = 0)
                  AND t.create_time >= ?
                  AND t.create_time <= ?
                ORDER BY t.create_time DESC, t.id DESC
                LIMIT 2000
                """,
                (rs, rowNum) -> new CourseTaskCandidate(
                        rs.getLong("task_id"),
                        rs.getLong("student_id"),
                        rs.getString("title"),
                        rs.getTimestamp("deadline")
                                .toLocalDateTime()
                ),
                now.minusDays(7),
                now
        );

        for (CourseTaskCandidate item : candidates) {
            try {
                notifications.createIfAbsent(
                        item.studentId(),
                        "COURSE_TASK_PUBLISHED",
                        item.taskId(),
                        "课程发布了新任务",
                        item.title()
                                + "，截止时间："
                                + item.deadline()
                );
            } catch (Exception e) {
                log.warn(
                        "Course task publish notification failed: taskId={}, studentId={}",
                        item.taskId(),
                        item.studentId(),
                        e
                );
            }
        }
    }

    /**
     * 公共课程任务截止提醒。
     *
     * 截止前24小时、6小时及逾期分别通知一次。
     * 学生已完成则跳过。
     */
    private void generateCourseTaskDeadlines(
            LocalDateTime now) {

        List<CourseTaskCandidate> candidates = jdbc.query(
                """
                SELECT
                    t.id AS task_id,
                    t.title,
                    t.deadline,
                    cs.student_id
                FROM learning_task t
                JOIN course c
                    ON c.id = t.course_id
                JOIN course_student cs
                    ON cs.course_id = t.course_id
                JOIN sys_user u
                    ON u.id = cs.student_id
                LEFT JOIN task_student ts
                    ON ts.task_id = t.id
                    AND ts.student_id = cs.student_id
                WHERE t.status = 1
                  AND c.status = 1
                  AND u.status = 1
                  AND u.role = 'STUDENT'
                  AND (ts.id IS NULL OR ts.completed = 0)
                  AND t.deadline >= ?
                  AND t.deadline <= ?
                ORDER BY t.deadline ASC, t.id ASC
                LIMIT 2000
                """,
                (rs, rowNum) -> new CourseTaskCandidate(
                        rs.getLong("task_id"),
                        rs.getLong("student_id"),
                        rs.getString("title"),
                        rs.getTimestamp("deadline")
                                .toLocalDateTime()
                ),
                now.minusDays(7),
                now.plusHours(24)
        );

        for (CourseTaskCandidate item : candidates) {

            try {
                long minutes = Duration.between(
                        now,
                        item.deadline()
                ).toMinutes();

                String bizType;
                String title;

                if (minutes > 360 && minutes <= 1440) {

                    bizType = "COURSE_TASK_T24H";
                    title = "课程任务将在24小时内截止";

                } else if (minutes > 0 && minutes <= 360) {

                    bizType = "COURSE_TASK_T6H";
                    title = "课程任务将在6小时内截止";

                } else if (minutes <= 0) {

                    bizType = "COURSE_TASK_OVERDUE";
                    title = "课程任务已逾期";

                } else {
                    continue;
                }

                notifications.createIfAbsent(
                        item.studentId(),
                        bizType,
                        item.taskId(),
                        title,
                        item.title()
                                + "，截止时间："
                                + item.deadline()
                );

            } catch (Exception e) {
                log.warn(
                        "Course task deadline notification failed: taskId={}, studentId={}",
                        item.taskId(),
                        item.studentId(),
                        e
                );
            }
        }
    }
}
