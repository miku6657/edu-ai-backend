package com.edu.ai.document.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;

import com.edu.ai.document.entity.CourseDocument;
import com.edu.ai.document.mapper.CourseDocumentMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class DocumentRecoveryJob {

    private static final Logger log =
            LoggerFactory.getLogger(DocumentRecoveryJob.class);

    private static final int TIMEOUT_MINUTES = 15;
    private static final int BATCH_SIZE = 20;

    private final CourseDocumentMapper documentMapper;
    private final DocumentIndexTask indexTask;
    private final DocumentLifecycleService lifecycleService;
    private final CourseIndexLease courseIndexLease;

    public DocumentRecoveryJob(
            CourseDocumentMapper documentMapper,
            DocumentIndexTask indexTask,
            DocumentLifecycleService lifecycleService,
            CourseIndexLease courseIndexLease) {

        this.documentMapper = documentMapper;
        this.indexTask = indexTask;
        this.lifecycleService = lifecycleService;
        this.courseIndexLease = courseIndexLease;
    }

    @Scheduled(initialDelay = 30000, fixedDelay = 60000)
    public void recover() {
        recoverProcessing();
        recoverUploaded();
        recoverDeleting();
    }

    /**
     * 恢复超时索引任务。
     *
     * 必须先取得课程锁，避免与仍在运行的索引任务竞争。
     */
    private void recoverProcessing() {

        LocalDateTime deadline =
                LocalDateTime.now().minusMinutes(TIMEOUT_MINUTES);

        List<CourseDocument> documents =
                documentMapper.selectList(
                        new LambdaQueryWrapper<CourseDocument>()
                                .eq(
                                        CourseDocument::getStatus,
                                        "PROCESSING"
                                )
                                .and(w -> w
                                        .le(
                                                CourseDocument::getProcessingStartedAt,
                                                deadline
                                        )
                                        .or(x -> x
                                                .isNull(
                                                        CourseDocument::getProcessingStartedAt
                                                )
                                                .le(
                                                        CourseDocument::getUpdateTime,
                                                        deadline
                                                )
                                        )
                                )
                                .orderByAsc(CourseDocument::getId)
                                .last("LIMIT " + BATCH_SIZE)
                );

        for (CourseDocument doc : documents) {

            CourseIndexLease.Handle lease =
                    courseIndexLease.tryOpen(
                            doc.getCourseId(),
                            "DOCUMENT_RECOVERY"
                    );

            if (lease == null) {
                continue;
            }

            try (lease) {

                lease.assertValid();

                boolean retry =
                        doc.getAttemptCount() == null
                        || doc.getAttemptCount()
                                < DocumentIndexTask.MAX_ATTEMPTS;

                String nextStatus =
                        retry ? "UPLOADED" : "FAILED";

                LocalDateTime now = LocalDateTime.now();

                LambdaUpdateWrapper<CourseDocument> update =
                        new LambdaUpdateWrapper<CourseDocument>()
                                .eq(
                                        CourseDocument::getId,
                                        doc.getId()
                                )
                                .eq(
                                        CourseDocument::getCourseId,
                                        doc.getCourseId()
                                )
                                .eq(
                                        CourseDocument::getStatus,
                                        "PROCESSING"
                                )
                                .set(
                                        CourseDocument::getStatus,
                                        nextStatus
                                )
                                .set(
                                        CourseDocument::getErrorMessage,
                                        retry
                                                ? "索引任务超时，等待重新处理"
                                                : "索引任务超时，已达到最大重试次数"
                                )
                                .set(
                                        CourseDocument::getProcessingToken,
                                        null
                                )
                                .set(
                                        CourseDocument::getProcessingStartedAt,
                                        null
                                )
                                .set(
                                        CourseDocument::getUpdateTime,
                                        now
                                );

                // 使用原执行令牌进行乐观条件校验。
                if (doc.getProcessingToken() == null) {
                    update.isNull(
                            CourseDocument::getProcessingToken
                    );
                } else {
                    update.eq(
                            CourseDocument::getProcessingToken,
                            doc.getProcessingToken()
                    );
                }

                // 防止将查询后发生变化的任务误恢复。
                if (doc.getProcessingStartedAt() == null) {
                    update.isNull(
                            CourseDocument::getProcessingStartedAt
                    );
                    update.le(
                            CourseDocument::getUpdateTime,
                            deadline
                    );
                } else {
                    update.eq(
                            CourseDocument::getProcessingStartedAt,
                            doc.getProcessingStartedAt()
                    );
                }

                lease.assertValid();

                int changed = documentMapper.update(null, update);

                if (changed == 1) {
                    log.warn(
                            "Recovered stale document: id={}, status={}",
                            doc.getId(),
                            nextStatus
                    );
                }

            } catch (Exception e) {
                log.error(
                        "Recover processing document failed: {}",
                        doc.getId(),
                        e
                );
            }
        }
    }

    /**
     * 扫描待索引文档。
     *
     * 获取课程锁失败时不修改状态。
     * 下一轮仍会重新扫描。
     */
    private void recoverUploaded() {

        List<CourseDocument> documents =
                documentMapper.selectList(
                        new LambdaQueryWrapper<CourseDocument>()
                                .eq(
                                        CourseDocument::getStatus,
                                        "UPLOADED"
                                )
                                .and(w -> w
                                        .isNull(
                                                CourseDocument::getAttemptCount
                                        )
                                        .or()
                                        .lt(
                                                CourseDocument::getAttemptCount,
                                                DocumentIndexTask.MAX_ATTEMPTS
                                        )
                                )
                                .orderByAsc(CourseDocument::getId)
                                .last("LIMIT " + BATCH_SIZE)
                );

        for (CourseDocument doc : documents) {
            try {
                indexTask.process(doc.getId());
            } catch (Exception e) {
                log.error(
                        "Schedule index failed: {}",
                        doc.getId(),
                        e
                );
            }
        }
    }

    /**
     * 恢复未完成的删除任务。
     */
    private void recoverDeleting() {

        List<CourseDocument> documents =
                documentMapper.selectList(
                        new LambdaQueryWrapper<CourseDocument>()
                                .in(
                                        CourseDocument::getStatus,
                                        "DELETING",
                                        "DELETE_FAILED"
                                )
                                .orderByAsc(CourseDocument::getId)
                                .last("LIMIT " + BATCH_SIZE)
                );

        for (CourseDocument doc : documents) {
            try {
                lifecycleService.resumeDelete(doc.getId());
            } catch (Exception e) {
                log.error(
                        "Recover deletion failed: {}",
                        doc.getId(),
                        e
                );
            }
        }
    }
}
