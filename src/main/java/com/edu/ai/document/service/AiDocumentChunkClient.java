package com.edu.ai.document.service;

import com.edu.ai.common.BusinessException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import java.util.Map;

@Component
public class AiDocumentChunkClient {

    private static final Logger log =
            LoggerFactory.getLogger(
                    AiDocumentChunkClient.class
            );

    private static final int MAX_RESPONSE_SIZE =
            32 * 1024 * 1024;

    private final ObjectMapper objectMapper;

    private final HttpClient httpClient;

    private final String serviceUrl;

    private final String internalKey;

    public AiDocumentChunkClient(
            ObjectMapper objectMapper,

            @Value("${ai.service.url}")
            String serviceUrl,

            @Value("${ai.service.internal-key}")
            String internalKey) {

        this.objectMapper = objectMapper;

        this.serviceUrl =
                serviceUrl.replaceAll("/+$", "");

        this.internalKey = internalKey;

        this.httpClient =
                HttpClient.newBuilder()
                        .version(
                                HttpClient.Version.HTTP_1_1
                        )
                        .connectTimeout(
                                Duration.ofSeconds(10)
                        )
                        .build();
    }

    /**
     * 读取当前Collection中的原始文档切片。
     */
    public JsonNode readChunks(
            Long courseId,
            Long documentId,
            String collectionName,
            int chunkingVersion) {

        try {

            String body =
                    objectMapper.writeValueAsString(
                            Map.of(
                                    "course_id",
                                    courseId,

                                    "document_id",
                                    documentId,

                                    "collection_name",
                                    collectionName,

                                    "chunking_version",
                                    chunkingVersion
                            )
                    );

            HttpRequest request =
                    HttpRequest.newBuilder()

                            .uri(
                                    URI.create(
                                            serviceUrl
                                            + "/internal/documents/chunks"
                                    )
                            )

                            .version(
                                    HttpClient.Version.HTTP_1_1
                            )

                            .timeout(
                                    Duration.ofSeconds(60)
                            )

                            .header(
                                    "Content-Type",
                                    "application/json"
                            )

                            .header(
                                    "Accept",
                                    "application/json"
                            )

                            .header(
                                    "X-Internal-Key",
                                    internalKey
                            )

                            .POST(
                                    HttpRequest.BodyPublishers
                                            .ofString(
                                                    body,
                                                    StandardCharsets.UTF_8
                                            )
                            )

                            .build();

            HttpResponse<InputStream> response =
                    httpClient.send(
                            request,
                            HttpResponse.BodyHandlers
                                    .ofInputStream()
                    );

            try (InputStream input = response.body()) {

                if (response.statusCode() == 404) {

                    throw new BusinessException(
                            409,
                            "当前知识库没有可补录的文档切片"
                    );
                }

                if (response.statusCode() != 200) {

                    log.warn(
                            "读取Python切片失败，"
                                    + "documentId={}, status={}",
                            documentId,
                            response.statusCode()
                    );

                    throw new BusinessException(
                            502,
                            "读取知识切片失败，请检查AI服务"
                    );
                }

                byte[] bytes = input.readNBytes(
                        MAX_RESPONSE_SIZE + 1
                );

                if (
                        bytes.length
                        > MAX_RESPONSE_SIZE
                ) {

                    throw new BusinessException(
                            502,
                            "知识切片响应数据超过限制"
                    );
                }

                return objectMapper.readTree(
                        new String(
                                bytes,
                                StandardCharsets.UTF_8
                        )
                );
            }

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();

            throw new BusinessException(
                    503,
                    "切片读取请求被中断"
            );

        } catch (IOException e) {

            log.error(
                    "调用Python切片接口失败，documentId={}",
                    documentId,
                    e
            );

            throw new BusinessException(
                    502,
                    "无法连接或读取AI切片服务"
            );
        }
    }
}
