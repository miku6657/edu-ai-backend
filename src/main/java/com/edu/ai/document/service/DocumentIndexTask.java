package com.edu.ai.document.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;

import com.edu.ai.course.entity.Course;
import com.edu.ai.course.mapper.CourseMapper;

import com.edu.ai.document.entity.CourseDocument;
import com.edu.ai.document.entity.CourseEmbeddingProfile;
import com.edu.ai.document.mapper.CourseDocumentMapper;

import tools.jackson.databind.ObjectMapper;

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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class DocumentIndexTask {

    public static final int MAX_ATTEMPTS = 3;

    private static final Logger log =
            LoggerFactory.getLogger(DocumentIndexTask.class);

    private final CourseDocumentMapper documentMapper;
    private final CourseMapper courseMapper;
    private final CourseIndexLease courseIndexLease;
    private final CourseEmbeddingProfileService profileService;
    private final ObjectMapper objectMapper;

    private final HttpClient httpClient;
    private final String serviceUrl;
    private final String internalKey;

    public DocumentIndexTask(
            CourseDocumentMapper documentMapper,
            CourseMapper courseMapper,
            CourseIndexLease courseIndexLease,
            CourseEmbeddingProfileService profileService,
            ObjectMapper objectMapper,
            @Value("${ai.service.url}") String serviceUrl,
            @Value("${ai.service.internal-key}") String internalKey) {

        this.documentMapper = documentMapper;
        this.courseMapper = courseMapper;
        this.courseIndexLease = courseIndexLease;
        this.profileService = profileService;
        this.objectMapper = objectMapper;

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

        if (initial == null || initial.getCourseId() == null) {
            return;
        }

        Long courseId = initial.getCourseId();

        CourseIndexLease.Handle lease =
                courseIndexLease.tryOpen(
                        courseId,
                        "DOCUMENT_INDEX"
                );

        if (lease == null) {
            log.info(
                    "Course index busy, documentId={}",
                    documentId
            );
            return;
        }

        try (lease) {
            processWithLock(
                    documentId,
                    courseId,
                    lease
            );
        }
    }

    private void processWithLock(
            Long documentId,
            Long courseId,
            CourseIndexLease.Handle lease) {

        String token = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();

        lease.assertValid();

        Course course = courseMapper.selectById(courseId);

        if (course == null) {
            return;
        }

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

        try {
            CourseDocument doc =
                    documentMapper.selectById(documentId);

            if (doc == null) {
                throw new IllegalStateException(
                        "索引文档不存在"
                );
            }

            lease.assertValid();

            int embeddingVersion =
                    course.getEmbeddingVersion() == null
                            ? 1
                            : course.getEmbeddingVersion();

            CourseEmbeddingProfile profile;

            if (embeddingVersion == 1) {
                profile = profileService.ensureInitial(
                        course.getUserId(),
                        courseId
                );
            } else {
                profile = profileService.require(
                        courseId,
                        embeddingVersion
                );
            }

            if (!"ACTIVE".equals(profile.getStatus())) {
                throw new IllegalStateException(
                        "当前课程Embedding Profile不是ACTIVE状态"
                );
            }

            String embeddingApiKey =
                    profileService.decryptKey(profile);

            Map<String, Object> payload =
                    new LinkedHashMap<>();

            payload.put("course_id", courseId);
            payload.put("document_id", documentId);
            payload.put("storage_name", doc.getStorageName());

            payload.put("embedding_version", embeddingVersion);
            payload.put(
                    "collection_name",
                    profile.getCollectionName()
            );

            payload.put(
                    "embedding_model",
                    profile.getModel()
            );

            payload.put(
                    "embedding_base_url",
                    profile.getBaseUrl()
            );

            payload.put(
                    "embedding_api_key",
                    embeddingApiKey
            );

            payload.put(
                    "expected_dimension",
                    profile.getDimension()
            );

            lease.assertValid();

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
                            HttpRequest.BodyPublishers.ofString(
                                    objectMapper.writeValueAsString(payload)
                            )
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
                                + ": "
                                + response.body()
                );
            }

            lease.assertValid();

            int updated = updateCurrentTask(
                    documentId,
                    token,
                    "READY",
                    null
            );

            if (updated != 1) {
                log.warn(
                        "Index result ignored, documentId={}",
                        documentId
                );
            } else {
                log.info(
                        "PDF indexed successfully, documentId={}, version={}",
                        documentId,
                        embeddingVersion
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
                        .eq(
                                CourseDocument::getId,
                                documentId
                        )
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
