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

    public NotificationProducerJob(
            NotificationService notifications,
            DdlTaskMapper ddlMapper,
            CourseDocumentMapper documentMapper,
            CourseEmbeddingMigrationMapper migrationMapper) {

        this.notifications = notifications;
        this.ddlMapper = ddlMapper;
        this.documentMapper = documentMapper;
        this.migrationMapper = migrationMapper;
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
}
