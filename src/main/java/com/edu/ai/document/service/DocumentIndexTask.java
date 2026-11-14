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
    private final HttpClient httpClient;
    private final String serviceUrl;
    private final String internalKey;

    public DocumentIndexTask(
            CourseDocumentMapper documentMapper,
            CourseMapper courseMapper,
            @Value("${ai.service.url}") String serviceUrl,
            @Value("${ai.service.internal-key}") String internalKey) {

        this.documentMapper = documentMapper;
        this.courseMapper = courseMapper;
        this.serviceUrl = serviceUrl;
        this.internalKey = internalKey;

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Async
    public void process(Long documentId) {

        String token = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();

        // 原子领取任务，并限制最大执行次数
        int claimed = documentMapper.update(
                null,
                new LambdaUpdateWrapper<CourseDocument>()
                        .eq(CourseDocument::getId, documentId)
                        .eq(CourseDocument::getStatus, "UPLOADED")
                        .and(w -> w
                                .isNull(CourseDocument::getAttemptCount)
                                .or()
                                .lt(CourseDocument::getAttemptCount,
                                        MAX_ATTEMPTS))
                        .set(CourseDocument::getStatus, "PROCESSING")
                        .set(CourseDocument::getProcessingToken, token)
                        .set(CourseDocument::getProcessingStartedAt, now)
                        .set(CourseDocument::getErrorMessage, null)
                        .set(CourseDocument::getUpdateTime, now)
                        .setSql(
                                "attempt_count = COALESCE("
                                + "attempt_count, 0) + 1"
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

        Course course = courseMapper.selectById(
                doc.getCourseId()
        );

        if (course == null) {
            updateCurrentTask(
                    documentId,
                    token,
                    "FAILED",
                    "所属课程不存在"
            );
            return;
        }

        int embeddingVersion =
                course.getEmbeddingVersion() == null
                        ? 1
                        : course.getEmbeddingVersion();

        try {
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
                            serviceUrl + "/internal/index"))
                    .timeout(Duration.ofMinutes(10))
                    .header(
                            "Content-Type",
                            "application/json")
                    .header(
                            "X-Internal-Key",
                            internalKey)
                    .POST(
                            HttpRequest.BodyPublishers
                                    .ofString(body))
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

            // 只有当前任务才能把文档改成 READY
            int updated = updateCurrentTask(
                    documentId,
                    token,
                    "READY",
                    null
            );

            if (updated == 1) {
                log.info(
                        "PDF indexed, documentId={}",
                        documentId
                );
            } else {
                log.warn(
                        "Old indexing result ignored, documentId={}",
                        documentId
                );
            }

        } catch (Exception e) {

            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }

            boolean retry =
                    doc.getAttemptCount() != null
                    && doc.getAttemptCount() < MAX_ATTEMPTS;

            // 未达到上限则重新进入待处理队列
            String nextStatus =
                    retry ? "UPLOADED" : "FAILED";

            updateCurrentTask(
                    documentId,
                    token,
                    nextStatus,
                    retry
                            ? "处理失败，等待自动重试"
                            : "处理失败，已达到最大重试次数"
            );

            log.error(
                    "PDF indexing failed, documentId={}, "
                            + "attempt={}",
                    documentId,
                    doc.getAttemptCount(),
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
                        .eq(CourseDocument::getStatus, "PROCESSING")
                        .eq(CourseDocument::getProcessingToken, token)
                        .set(CourseDocument::getStatus, nextStatus)
                        .set(CourseDocument::getErrorMessage,
                                errorMessage)
                        .set(CourseDocument::getProcessingToken,
                                null)
                        .set(CourseDocument::getProcessingStartedAt,
                                null)
                        .set(CourseDocument::getUpdateTime,
                                LocalDateTime.now())
        );
    }
}
