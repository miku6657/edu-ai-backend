package com.edu.ai.document.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import com.edu.ai.document.entity.CourseDocument;
import com.edu.ai.document.entity.CourseEmbeddingMigration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class AiEmbeddingMigrationClient {

    private final String serviceUrl;
    private final String internalKey;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public AiEmbeddingMigrationClient(
            @Value("${ai.service.url}") String serviceUrl,
            @Value("${ai.service.internal-key}") String internalKey,
            ObjectMapper objectMapper) {

        this.serviceUrl = serviceUrl;
        this.internalKey = internalKey;
        this.objectMapper = objectMapper;

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    public MigrationResult migrate(
            CourseEmbeddingMigration migration,
            List<CourseDocument> documents,
            String apiKey) {

        try {
            Map<String, Object> body = new LinkedHashMap<>();

            body.put("course_id", migration.getCourseId());
            body.put("source_version", migration.getSourceVersion());
            body.put("target_version", migration.getTargetVersion());

            body.put(
                    "provider",
                    migration.getTargetProvider()
            );

            body.put(
                    "base_url",
                    migration.getTargetBaseUrl()
            );

            body.put(
                    "api_key",
                    apiKey == null ? "" : apiKey
            );

            body.put(
                    "model",
                    migration.getTargetModel()
            );

            body.put(
                    "expected_dimension",
                    migration.getTargetDimension()
            );

            body.put(
                    "documents",
                    documents.stream()
                            .map(document -> Map.of(
                                    "document_id",
                                    document.getId(),
                                    "storage_name",
                                    document.getStorageName()
                            ))
                            .toList()
            );

            String json = objectMapper.writeValueAsString(body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(
                            serviceUrl + "/internal/embedding/migrate"
                    ))
                    .timeout(Duration.ofMinutes(30))
                    .header("Content-Type", "application/json")
                    .header("X-Internal-Key", internalKey)
                    .POST(
                            HttpRequest.BodyPublishers.ofString(json)
                    )
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() != 200) {
                throw new IllegalStateException(
                        "Embedding迁移服务异常，HTTP "
                                + response.statusCode()
                );
            }

            JsonNode root = objectMapper.readTree(response.body());

            if (!"SUCCEEDED".equals(
                    root.path("status").asText())) {

                throw new IllegalStateException(
                        "Python没有确认迁移成功"
                );
            }

            MigrationResult result = new MigrationResult();

            result.courseId = root.path("course_id").asLong();
            result.sourceVersion =
                    root.path("source_version").asInt();

            result.targetVersion =
                    root.path("target_version").asInt();

            result.totalDocuments =
                    root.path("total_documents").asInt();

            result.indexedChunks =
                    root.path("indexed_chunks").asInt();

            result.dimension =
                    root.path("dimension").asInt();

            result.status = root.path("status").asText();

            return result;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Embedding迁移调用被中断", e);

        } catch (Exception e) {
            throw new IllegalStateException(
                    "调用Python迁移服务失败", e);
        }
    }

    public static class MigrationResult {

        public long courseId;

        public int sourceVersion;

        public int targetVersion;

        public int totalDocuments;

        public int indexedChunks;

        public int dimension;

        public String status;
    }
}
