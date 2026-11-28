package com.edu.ai.document.service;

import com.edu.ai.common.BusinessException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class AiDocumentSummaryClient {

    private final ObjectMapper objectMapper;

    private final HttpClient httpClient;

    private final String serviceUrl;

    private final String internalKey;

    public AiDocumentSummaryClient(
            ObjectMapper objectMapper,

            @Value("${ai.service.url}")
            String serviceUrl,

            @Value("${ai.service.internal-key}")
            String internalKey) {

        this.objectMapper = objectMapper;

        this.serviceUrl =
                serviceUrl.replaceAll("/+$", "");

        this.internalKey = internalKey;

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public record GeneratedSummary(
            String summary,
            JsonNode outline
    ) {}

    /**
     * 调用Python服务生成摘要和大纲。
     */
    public GeneratedSummary generate(
            String documentTitle,
            List<DocumentSummarySourceReader.SourceChunk> chunks,
            String model,
            String apiKey,
            String baseUrl) {

        try {

            Map<String, Object> body =
                    new LinkedHashMap<>();

            body.put("document_title", documentTitle);
            body.put("model", model);
            body.put("api_key", apiKey);
            body.put("base_url", baseUrl);

            List<Map<String, Object>> chunkItems =
                    new ArrayList<>();

            for (var chunk : chunks) {

                Map<String, Object> item =
                        new LinkedHashMap<>();

                item.put("page", chunk.page());
                item.put("chunk_index", chunk.chunkIndex());
                item.put("section_title", chunk.sectionTitle());
                item.put("text", chunk.content());

                chunkItems.add(item);
            }

            body.put("chunks", chunkItems);

            String requestBody =
                    objectMapper.writeValueAsString(body);

            HttpRequest request =
                    HttpRequest.newBuilder()
                            .uri(URI.create(
                                    serviceUrl
                                            + "/internal/documents/summary/generate"
                            ))
                            .timeout(Duration.ofMinutes(3))
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
                                    HttpRequest.BodyPublishers.ofString(
                                            requestBody,
                                            StandardCharsets.UTF_8
                                    )
                            )
                            .build();

            HttpResponse<String> response =
                    httpClient.send(
                            request,
                            HttpResponse.BodyHandlers.ofString(
                                    StandardCharsets.UTF_8
                            )
                    );

            if (response.statusCode() != 200) {

                throw new BusinessException(
                        response.statusCode() == 400
                                ? 400
                                : response.statusCode() == 413
                                    ? 413
                                    : 502,
                        "AI摘要生成失败，请检查资料内容和模型服务"
                );
            }

            JsonNode root =
                    objectMapper.readTree(response.body());

            JsonNode summaryNode = root.get("summary");
            JsonNode outlineNode = root.get("outline");

            if (summaryNode == null
                    || !summaryNode.isString()
                    || outlineNode == null
                    || !outlineNode.isArray()
                    || outlineNode.isEmpty()) {

                throw new BusinessException(
                        502,
                        "AI返回的摘要或大纲格式不正确"
                );
            }

            String summary = summaryNode.asString().trim();

            if (summary.isBlank()) {

                throw new BusinessException(
                        502,
                        "AI返回的摘要为空"
                );
            }

            return new GeneratedSummary(
                    summary,
                    outlineNode
            );

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();

            throw new BusinessException(
                    503,
                    "AI摘要生成请求被中断"
            );

        } catch (IOException e) {

            throw new BusinessException(
                    502,
                    "无法连接AI摘要生成服务"
            );
        }
    }
}
