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

    // 索引请求最多等待10分钟，这里给15分钟恢复窗口
    private static final int TIMEOUT_MINUTES = 15;

    private final CourseDocumentMapper documentMapper;
    private final DocumentIndexTask indexTask;
    private final DocumentLifecycleService lifecycleService;

    public DocumentRecoveryJob(
            CourseDocumentMapper documentMapper,
            DocumentIndexTask indexTask,
            DocumentLifecycleService lifecycleService) {

        this.documentMapper = documentMapper;
        this.indexTask = indexTask;
        this.lifecycleService = lifecycleService;
    }

    @Scheduled(
            initialDelay = 30000,
            fixedDelay = 60000
    )
    public void recover() {

        recoverProcessing();
        recoverUploaded();
        recoverDeleting();
    }

    // 恢复长时间卡住的 PROCESSING 文档
    private void recoverProcessing() {

        LocalDateTime deadline =
                LocalDateTime.now()
                        .minusMinutes(TIMEOUT_MINUTES);

        List<CourseDocument> documents =
                documentMapper.selectList(
                        new LambdaQueryWrapper<CourseDocument>()
                                .eq(CourseDocument::getStatus,
                                        "PROCESSING")
                                .and(w -> w
                                        .le(
                                                CourseDocument
                                                        ::getProcessingStartedAt,
                                                deadline)
                                        .or(x -> x
                                                .isNull(
                                                        CourseDocument
                                                                ::getProcessingStartedAt)
                                                .le(
                                                        CourseDocument
                                                                ::getUpdateTime,
                                                        deadline)))
                                .orderByAsc(CourseDocument::getId)
                                .last("LIMIT 20")
                );

        for (CourseDocument doc : documents) {

            try {
                boolean retry =
                        doc.getAttemptCount() == null
                        || doc.getAttemptCount()
                                < DocumentIndexTask.MAX_ATTEMPTS;

                String nextStatus =
                        retry ? "UPLOADED" : "FAILED";

                LambdaUpdateWrapper<CourseDocument> wrapper =
                        new LambdaUpdateWrapper<CourseDocument>()
                                .eq(CourseDocument::getId,
                                        doc.getId())
                                .eq(CourseDocument::getStatus,
                                        "PROCESSING")
                                .set(CourseDocument::getStatus,
                                        nextStatus)
                                .set(CourseDocument::getErrorMessage,
                                        retry
                                                ? "任务超时，等待恢复"
                                                : "任务超时，超过重试次数")
                                .set(CourseDocument::getProcessingToken,
                                        null)
                                .set(CourseDocument
                                                ::getProcessingStartedAt,
                                        null)
                                .set(CourseDocument::getUpdateTime,
                                        LocalDateTime.now());

                // 加上任务标识，避免清除新任务
                if (doc.getProcessingToken() == null) {
                    wrapper.isNull(
                            CourseDocument::getProcessingToken);
                } else {
                    wrapper.eq(
                            CourseDocument::getProcessingToken,
                            doc.getProcessingToken());
                }

                // 旧数据可能没有 processingStartedAt
                if (doc.getProcessingStartedAt() == null) {
                    wrapper.isNull(
                            CourseDocument
                                    ::getProcessingStartedAt);
                    wrapper.le(
                            CourseDocument::getUpdateTime,
                            deadline);
                } else {
                    wrapper.eq(
                            CourseDocument
                                    ::getProcessingStartedAt,
                            doc.getProcessingStartedAt());
                }

                int updated =
                        documentMapper.update(null, wrapper);

                if (updated == 1) {
                    log.warn(
                            "Recovered stale indexing task: {}, status={}",
                            doc.getId(),
                            nextStatus
                    );
                }

            } catch (Exception e) {
                log.error(
                        "Recover indexing task failed: {}",
                        doc.getId(), e);
            }
        }
    }

    // 恢复待处理队列
    private void recoverUploaded() {

        List<CourseDocument> documents =
                documentMapper.selectList(
                        new LambdaQueryWrapper<CourseDocument>()
                                .eq(CourseDocument::getStatus,
                                        "UPLOADED")
                                .lt(CourseDocument::getAttemptCount,
                                        DocumentIndexTask.MAX_ATTEMPTS)
                                .orderByAsc(CourseDocument::getId)
                                .last("LIMIT 20")
                );

        for (CourseDocument doc : documents) {
            try {
                indexTask.process(doc.getId());
            } catch (Exception e) {
                log.error(
                        "Schedule index failed: {}",
                        doc.getId(), e);
            }
        }
    }

    // 恢复未完成删除任务
    private void recoverDeleting() {

        List<CourseDocument> documents =
                documentMapper.selectList(
                        new LambdaQueryWrapper<CourseDocument>()
                                .in(CourseDocument::getStatus,
                                        "DELETING",
                                        "DELETE_FAILED")
                                .orderByAsc(CourseDocument::getId)
                                .last("LIMIT 20")
                );

        for (CourseDocument doc : documents) {
            try {
                lifecycleService.resumeDelete(doc.getId());
            } catch (Exception e) {
                log.error(
                        "Recover deletion failed: {}",
                        doc.getId(), e);
            }
        }
    }
}
