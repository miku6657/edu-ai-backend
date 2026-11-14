package com.edu.ai.document.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;

import com.edu.ai.course.entity.Course;
import com.edu.ai.course.mapper.CourseMapper;

import com.edu.ai.document.entity.CourseDocument;
import com.edu.ai.document.mapper.CourseDocumentMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class DocumentIndexTask {

    public static final int MAX_ATTEMPTS = 3;

    private static final Logger log =
            LoggerFactory.getLogger(DocumentIndexTask.class);

    private final CourseDocumentMapper documentMapper;
    private final CourseMapper courseMapper;
    private final CourseIndexLockService indexLockService;

    private final HttpClient httpClient;

    private final String serviceUrl;
    private final String internalKey;

    public DocumentIndexTask(
            CourseDocumentMapper documentMapper,
            CourseMapper courseMapper,
            CourseIndexLockService indexLockService,
            @Value("${ai.service.url}") String serviceUrl,
            @Value("${ai.service.internal-key}") String internalKey) {

        this.documentMapper = documentMapper;
        this.courseMapper = courseMapper;
        this.indexLockService = indexLockService;

        this.serviceUrl = serviceUrl;
        this.internalKey = internalKey;

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Async
    public void process(Long documentId) {

        if (documentId == null || documentId <= 0) {
            return;
        }

        CourseDocument initial =
                documentMapper.selectById(documentId);

        if (initial == null) {
            return;
        }

        Long courseId = initial.getCourseId();

        if (courseId == null) {
            return;
        }

        // 先获取课程级索引锁。
        String lockToken = indexLockService.tryAcquire(
                courseId,
                "DOCUMENT_INDEX"
        );

        if (lockToken == null) {
            log.info(
                    "Course index locked, skip this attempt: documentId={}",
                    documentId
            );
            return;
        }

        try {
            processWithLock(
                    documentId,
                    courseId,
                    lockToken
            );

        } finally {
            indexLockService.release(
                    courseId,
                    lockToken
            );
        }
    }

    private void processWithLock(
            Long documentId,
            Long courseId,
            String lockToken) {

        String token = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();

        // 锁获取后再检查课程状态。
        Course course = courseMapper.selectById(courseId);

        if (course == null) {
            return;
        }

        // 原子领取任务。
        int claimed = documentMapper.update(
                null,
                new LambdaUpdateWrapper<CourseDocument>()
                        .eq(CourseDocument::getId, documentId)
                        .eq(CourseDocument::getCourseId, courseId)
                        .eq(CourseDocument::getStatus, "UPLOADED")
                        .and(w -> w
                                .isNull(CourseDocument::getAttemptCount)
                                .or()
                                .lt(
                                        CourseDocument::getAttemptCount,
                                        MAX_ATTEMPTS
                                )
                        )
                        .set(
                                CourseDocument::getStatus,
                                "PROCESSING"
                        )
                        .set(
                                CourseDocument::getProcessingToken,
                                token
                        )
                        .set(
                                CourseDocument::getProcessingStartedAt,
                                now
                        )
                        .set(
                                CourseDocument::getErrorMessage,
                                null
                        )
                        .set(
                                CourseDocument::getUpdateTime,
                                now
                        )
                        .setSql(
                                "attempt_count = COALESCE(attempt_count, 0) + 1"
                        )
        );

        if (claimed != 1) {
            return;
        }

        CourseDocument doc =
                documentMapper.selectById(documentId);

        if (doc == null) {
            return;
        }

        int embeddingVersion =
                course.getEmbeddingVersion() == null
                        ? 1
                        : course.getEmbeddingVersion();

        try {
            // 调用 Python 之前再次确认锁有效。
            if (!indexLockService.isOwned(
                    courseId,
                    lockToken)) {

                throw new IllegalStateException(
                        "课程索引锁已经失效"
                );
            }

            String body = """
                    {
                      "course_id": %d,
                      "document_id": %d,
                      "storage_name": "%s",
                      "embedding_version": %d
                    }
                    """.formatted(
                    doc.getCourseId(),
                    doc.getId(),
                    doc.getStorageName(),
                    embeddingVersion
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(
                            serviceUrl + "/internal/index"
                    ))
                    .timeout(Duration.ofMinutes(10))
                    .header(
                            "Content-Type",
                            "application/json"
                    )
                    .header(
                            "X-Internal-Key",
                            internalKey
                    )
                    .POST(
                            HttpRequest.BodyPublishers.ofString(body)
                    )
                    .build();

            HttpResponse<String> response =
                    httpClient.send(
                            request,
                            HttpResponse.BodyHandlers.ofString()
                    );

            if (response.statusCode() != 200) {
                throw new IllegalStateException(
                        "Python indexing HTTP "
                                + response.statusCode()
                );
            }

            // 即使 Python 返回成功，锁丢失也不能更新 READY。
            if (!indexLockService.isOwned(
                    courseId,
                    lockToken)) {

                throw new IllegalStateException(
                        "索引期间课程锁失效，禁止确认索引成功"
                );
            }

            int updated = updateCurrentTask(
                    documentId,
                    token,
                    "READY",
                    null
            );

            if (updated == 1) {
                log.info(
                        "PDF indexed successfully, documentId={}, version={}",
                        documentId,
                        embeddingVersion
                );
            } else {
                log.warn(
                        "Index result ignored, documentId={}",
                        documentId
                );
            }

        } catch (Exception e) {

            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }

            CourseDocument latest =
                    documentMapper.selectById(documentId);

            boolean retry =
                    latest != null
                    && latest.getAttemptCount() != null
                    && latest.getAttemptCount() < MAX_ATTEMPTS;

            updateCurrentTask(
                    documentId,
                    token,
                    retry ? "UPLOADED" : "FAILED",
                    retry
                            ? "索引失败，等待重试"
                            : "索引失败，已达到最大重试次数"
            );

            log.error(
                    "PDF indexing failed, documentId={}",
                    documentId,
                    e
            );
        }
    }

    private int updateCurrentTask(
            Long documentId,
            String token,
            String nextStatus,
            String errorMessage) {

        return documentMapper.update(
                null,
                new LambdaUpdateWrapper<CourseDocument>()
                        .eq(CourseDocument::getId, documentId)
                        .eq(
                                CourseDocument::getStatus,
                                "PROCESSING"
                        )
                        .eq(
                                CourseDocument::getProcessingToken,
                                token
                        )
                        .set(
                                CourseDocument::getStatus,
                                nextStatus
                        )
                        .set(
                                CourseDocument::getErrorMessage,
                                errorMessage
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
                                LocalDateTime.now()
                        )
        );
    }
}
