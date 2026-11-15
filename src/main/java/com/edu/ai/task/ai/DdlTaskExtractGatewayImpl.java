package com.edu.ai.task.ai;

import com.edu.ai.common.BusinessException;
import com.edu.ai.task.dto.DdlTaskExtractedData;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;

@Component
public class DdlTaskExtractGatewayImpl
        implements DdlTaskExtractGateway {

    private final ObjectMapper objectMapper;

    private final HttpClient httpClient;

    private final String serviceUrl;

    private final String internalKey;

    public DdlTaskExtractGatewayImpl(
            ObjectMapper objectMapper,
            @Value("${ai.service.url}") String serviceUrl,
            @Value("${ai.service.internal-key}") String internalKey) {

        this.objectMapper = objectMapper;
        this.serviceUrl = serviceUrl;
        this.internalKey = internalKey;

        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public DdlTaskExtractedData extract(
            String sourceText,
            String courseName,
            String timezone,
            LocalDateTime currentTime) {

        try {

            String prompt = DdlTaskExtractPrompt.build(
                    sourceText,
                    courseName,
                    currentTime,
                    timezone
            );

            var body = objectMapper.createObjectNode();

            body.put("source_text", sourceText);
            body.put("course_name",
                    courseName == null ? "" : courseName);
            body.put("timezone", timezone);
            body.put("current_time", currentTime.toString());
            body.put("prompt", prompt);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(
                            serviceUrl + "/internal/ddl/extract"
                    ))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("X-Internal-Key", internalKey)
                    .POST(
                            HttpRequest.BodyPublishers.ofString(
                                    objectMapper.writeValueAsString(body),
                                    StandardCharsets.UTF_8
                            )
                    )
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(
                            StandardCharsets.UTF_8
                    )
            );

            if (response.statusCode() != 200) {
                throw new BusinessException(
                        502,
                        "AI 任务提取服务调用失败，HTTP "
                                + response.statusCode()
                );
            }

            JsonNode root = objectMapper.readTree(
                    response.body()
            );

            // 兼容 Python 直接返回对象和 data 包装两种格式
            JsonNode data = root.has("data")
                    ? root.get("data")
                    : root;

            if (data == null || !data.isObject()) {
                throw new BusinessException(
                        502,
                        "AI 服务返回的数据格式错误"
                );
            }

            return objectMapper.treeToValue(
                    data,
                    DdlTaskExtractedData.class
            );

        } catch (BusinessException e) {

            throw e;

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();

            throw new BusinessException(
                    502,
                    "AI 任务提取请求被中断"
            );

        } catch (IOException e) {

            throw new BusinessException(
                    502,
                    "AI 任务提取失败，请检查 AI 服务"
            );
        }
    }
}
