package com.edu.ai.document.service;

import com.edu.ai.common.BusinessException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
public class PdfMergeClient {

    private static final int MAX_FILE_SIZE =
            30 * 1024 * 1024;

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

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(
                            serviceUrl
                            + "/internal/documents/merge"
                    ))
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
                            HttpRequest.BodyPublishers
                                    .ofString(json)
                    )
                    .build();

            HttpResponse<InputStream> response =
                    httpClient.send(
                            request,
                            HttpResponse.BodyHandlers
                                    .ofInputStream()
                    );

            try (InputStream input = response.body()) {

                if (response.statusCode() == 400) {

                    throw new BusinessException(
                            400,
                            "PDF合并条件不满足，请检查文件、页数和大小"
                    );
                }

                if (response.statusCode() != 200) {

                    throw new BusinessException(
                            502,
                            "Python PDF合并服务执行失败"
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

            throw new BusinessException(
                    502, "无法连接或读取PDF合并服务"
            );
        }
    }
}
