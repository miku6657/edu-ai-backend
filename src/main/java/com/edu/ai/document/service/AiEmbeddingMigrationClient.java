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

            body.put(
                    "migration_id",
                    migration.getId()
            );

            body.put(
                    "execution_token",
                    migration.getProcessingToken()
            );

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

            if (!"STAGED".equals(
                    root.path("status").asText())) {

                throw new IllegalStateException(
                        "Python没有确认迁移已暂存"
                );
            }

            MigrationResult result = new MigrationResult();

            result.migrationId =
                    root.path("migration_id").asLong();

            result.executionToken =
                    root.path("execution_token").asText();

            result.collectionName =
                    root.path("collection_name").asText();

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

            // 严格校验执行者身份与临时 Collection 名称。
            if (result.migrationId != migration.getId()
                    || !migration.getProcessingToken().equals(
                            result.executionToken)) {

                throw new IllegalStateException(
                        "迁移执行者不匹配"
                );
            }

            if (result.collectionName == null
                    || !result.collectionName.matches(
                            "mig_[0-9]+_[a-f0-9]{24}"
                    )) {

                throw new IllegalStateException(
                        "Python返回的临时Collection名称不合法"
                );
            }

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

        public long migrationId;

        public String executionToken;

        public String collectionName;

        public long courseId;

        public int sourceVersion;

        public int targetVersion;

        public int totalDocuments;

        public int indexedChunks;

        public int dimension;

        public String status;
    }
}
