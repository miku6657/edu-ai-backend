package com.edu.ai.document.service;

import com.edu.ai.common.BusinessException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
public class PdfMergeClient {

    private static final int MAX_FILE_SIZE =
            30 * 1024 * 1024;

    private static final Logger log =
            LoggerFactory.getLogger(PdfMergeClient.class);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    private final String serviceUrl;
    private final String internalKey;

    public PdfMergeClient(
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
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * 请求Python生成合并后的PDF。
     */
    public byte[] merge(List<String> storageNames) {

        try {

            String json = objectMapper.writeValueAsString(
                    Map.of(
                            "storage_names",
                            storageNames
                    )
            );

            log.info("PDF合并请求参数: {}", json);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(
                            serviceUrl
                            + "/internal/documents/merge"
                    ))
                    .version(HttpClient.Version.HTTP_1_1)
                    .timeout(Duration.ofSeconds(120))
                    .header(
                            "Content-Type",
                            "application/json"
                    )
                    .header(
                            "Accept",
                            "application/pdf"
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

            HttpResponse<InputStream> response =
                    httpClient.send(
                            request,
                            HttpResponse.BodyHandlers
                                    .ofInputStream()
                    );

            try (InputStream input = response.body()) {

                log.info(
                        "PDF合并服务返回状态码: {}, Content-Type: {}",
                        response.statusCode(),
                        response.headers()
                                .firstValue("Content-Type")
                                .orElse("unknown")
                );

                if (response.statusCode() == 400) {

                    String errorBody = new String(
                            input.readNBytes(8192),
                            StandardCharsets.UTF_8
                    );

                    String message = "PDF合并条件不满足";

                    try {

                        JsonNode errorNode =
                                objectMapper.readTree(errorBody);

                        JsonNode detail = errorNode.get("detail");

                        if (detail != null
                                && detail.isString()) {

                            message = detail.asString();
                        }

                    } catch (Exception ignored) {
                        // 无法解析Python错误时使用默认提示。
                    }

                    throw new BusinessException(
                            400,
                            message
                    );
                }

                if (response.statusCode() != 200) {

                    String errorBody = new String(
                            input.readNBytes(8192),
                            StandardCharsets.UTF_8
                    );

                    log.error(
                            "Python PDF合并失败，status={}, body={}",
                            response.statusCode(),
                            errorBody
                    );

                    throw new BusinessException(
                            502,
                            "Python PDF合并失败，状态码："
                                    + response.statusCode()
                    );
                }

                String contentType =
                        response.headers()
                                .firstValue("Content-Type")
                                .orElse("");

                if (!contentType.startsWith(
                        "application/pdf")) {

                    throw new BusinessException(
                            502,
                            "合并服务未返回有效PDF"
                    );
                }

                byte[] data = input.readNBytes(
                        MAX_FILE_SIZE + 1
                );

                if (data.length == 0
                        || data.length > MAX_FILE_SIZE) {

                    throw new BusinessException(
                            400,
                            "合并结果为空或超过30MB"
                    );
                }

                return data;
            }

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();

            throw new BusinessException(
                    503, "PDF合并任务被中断"
            );

        } catch (IOException e) {

            log.error(
                    "调用Python PDF合并接口失败，url={}",
                    serviceUrl + "/internal/documents/merge",
                    e
            );

            throw new BusinessException(
                    502, "无法连接或读取PDF合并服务"
            );
        }
    }
}
