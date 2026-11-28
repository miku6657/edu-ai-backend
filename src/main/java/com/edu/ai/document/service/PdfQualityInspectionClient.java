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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

@Component
public class PdfQualityInspectionClient {

    private final ObjectMapper objectMapper;

    private final HttpClient httpClient;

    private final String serviceUrl;

    private final String internalKey;

    public PdfQualityInspectionClient(
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

    public record InspectionResult(

            int pageCount,

            List<Integer> pagesWithoutText,

            List<Integer> imageOnlyPages,

            List<Integer> parseErrorPages

    ) {}

    public InspectionResult inspect(
            String storageName) {

        try {

            Map<String, Object> body =
                    new LinkedHashMap<>();

            body.put(
                    "storage_name",
                    storageName
            );

            String json =
                    objectMapper.writeValueAsString(body);

            HttpRequest request =
                    HttpRequest.newBuilder()
                            .uri(URI.create(
                                    serviceUrl
                                            + "/internal/documents/quality/inspect"
                            ))
                            .timeout(Duration.ofSeconds(60))
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
                                            json,
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
                        502,
                        "PDF质量检查服务调用失败"
                );
            }

            JsonNode root =
                    objectMapper.readTree(response.body());

            int pageCount =
                    root.path("page_count").asInt(-1);

            if (pageCount <= 0) {

                throw new BusinessException(
                        502,
                        "PDF质量检查返回的页数无效"
                );
            }

            return new InspectionResult(
                    pageCount,
                    readIntegerList(root, "pages_without_text"),
                    readIntegerList(root, "image_only_pages"),
                    readIntegerList(root, "parse_error_pages")
            );

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();

            throw new BusinessException(
                    503,
                    "PDF质量检查请求被中断"
            );

        } catch (IOException e) {

            throw new BusinessException(
                    502,
                    "无法连接PDF质量检查服务"
            );
        }
    }

    private List<Integer> readIntegerList(
            JsonNode root,
            String field) {

        JsonNode node = root.get(field);

        if (node == null || !node.isArray()) {
            throw new BusinessException(
                    502,
                    "PDF质量检查响应格式不正确：" + field
            );
        }

        List<Integer> result = new ArrayList<>();

        for (JsonNode item : node) {

            if (!item.canConvertToInt()) {
                throw new BusinessException(
                        502,
                        "PDF质量检查页码格式不正确"
                );
            }

            result.add(item.asInt());
        }

        return result;
    }
}
