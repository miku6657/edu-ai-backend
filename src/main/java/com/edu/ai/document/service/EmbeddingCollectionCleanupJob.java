package com.edu.ai.document.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import com.edu.ai.document.entity.CourseEmbeddingMigration;
import com.edu.ai.document.entity.CourseEmbeddingProfile;

import com.edu.ai.document.mapper.CourseEmbeddingMigrationMapper;
import com.edu.ai.document.mapper.CourseEmbeddingProfileMapper;

import tools.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class EmbeddingCollectionCleanupJob {

    private static final Logger log =
            LoggerFactory.getLogger(
                    EmbeddingCollectionCleanupJob.class
            );

    private static final int MIN_AGE_HOURS = 48;

    private final CourseEmbeddingProfileMapper profileMapper;
    private final CourseEmbeddingMigrationMapper migrationMapper;

    private final ObjectMapper objectMapper;

    private final HttpClient httpClient;

    private final String serviceUrl;
    private final String internalKey;

    public EmbeddingCollectionCleanupJob(
            CourseEmbeddingProfileMapper profileMapper,
            CourseEmbeddingMigrationMapper migrationMapper,
            ObjectMapper objectMapper,
            @Value("${ai.service.url}") String serviceUrl,
            @Value("${ai.service.internal-key}") String internalKey) {

        this.profileMapper = profileMapper;
        this.migrationMapper = migrationMapper;
        this.objectMapper = objectMapper;

        this.serviceUrl = serviceUrl;
        this.internalKey = internalKey;

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * 每6小时清理一次过期迁移 Collection。
     */
    @Scheduled(
            initialDelay = 60000,
            fixedDelay = 21600000
    )
    public void cleanup() {

        try {

            Set<String> keep =
                    collectReferencedCollections();

            Map<String, Object> body =
                    new LinkedHashMap<>();

            body.put(
                    "keep_collection_names",
                    new ArrayList<>(keep)
            );

            body.put(
                    "min_age_hours",
                    MIN_AGE_HOURS
            );

            String json =
                    objectMapper.writeValueAsString(body);

            HttpRequest request =
                    HttpRequest.newBuilder()
                            .uri(URI.create(
                                    serviceUrl
                                            + "/internal/embedding/collections/cleanup"
                            ))
                            .timeout(Duration.ofMinutes(3))
                            .header(
                                    "Content-Type",
                                    "application/json"
                            )
                            .header(
                                    "X-Internal-Key",
                                    internalKey
                            )
                            .POST(
                                    HttpRequest.BodyPublishers.ofString(json)
                            )
                            .build();

            HttpResponse<String> response =
                    httpClient.send(
                            request,
                            HttpResponse.BodyHandlers.ofString()
                    );

            if (response.statusCode() != 200) {

                throw new IllegalStateException(
                        "Collection清理失败，HTTP "
                                + response.statusCode()
                                + "，Python响应："
                                + response.body()
                );
            }

            log.info(
                    "Embedding Collection cleanup completed: {}",
                    response.body()
            );

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();

            log.error(
                    "Embedding Collection cleanup interrupted",
                    e
            );

        } catch (Exception e) {

            log.error(
                    "Embedding Collection cleanup failed",
                    e
            );
        }
    }

    /**
     * 收集所有仍然被引用的 Collection。
     */
    private Set<String> collectReferencedCollections() {

        Set<String> keep = new HashSet<>();

        // 1. 所有历史 Profile 都保留。
        List<CourseEmbeddingProfile> profiles =
                profileMapper.selectList(null);

        for (CourseEmbeddingProfile profile : profiles) {

            if (profile.getCollectionName() != null
                    && !profile.getCollectionName().isBlank()) {

                keep.add(
                        profile.getCollectionName()
                );
            }
        }

        // 2. 保留当前 RUNNING 迁移任务的 Collection。
        List<CourseEmbeddingMigration> running =
                migrationMapper.selectList(
                        new LambdaQueryWrapper<CourseEmbeddingMigration>()
                                .eq(
                                        CourseEmbeddingMigration::getStatus,
                                        "RUNNING"
                                )
                );

        for (CourseEmbeddingMigration migration : running) {

            if (migration.getTargetCollectionName() != null
                    && !migration.getTargetCollectionName().isBlank()) {

                keep.add(
                        migration.getTargetCollectionName()
                );
            }

            String token =
                    migration.getProcessingToken();

            if (token != null && !token.isBlank()) {

                keep.add(
                        migrationCollectionName(
                                migration.getId(),
                                token
                        )
                );
            }
        }

        return keep;
    }

    /**
     * 必须与 Python 端 migration_collection_name 完全一致。
     */
    private String migrationCollectionName(
            Long migrationId,
            String token) {

        try {

            byte[] digest = MessageDigest
                    .getInstance("SHA-256")
                    .digest(
                            token.getBytes(
                                    StandardCharsets.UTF_8
                            )
                    );

            String hash = HexFormat.of()
                    .formatHex(digest)
                    .substring(0, 24);

            return "mig_" + migrationId + "_" + hash;

        } catch (Exception e) {

            throw new IllegalStateException(
                    "迁移Collection名称计算失败",
                    e
            );
        }
    }
}
