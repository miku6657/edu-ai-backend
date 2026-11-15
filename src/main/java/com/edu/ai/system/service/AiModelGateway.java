package com.edu.ai.system.service;

import com.edu.ai.common.BusinessException;
import com.edu.ai.system.dto.ModelProbeRequest;
import com.edu.ai.system.dto.ModelProbeResultVO;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class AiModelGateway {

    private final String serviceUrl;
    private final String internalKey;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public AiModelGateway(
            @Value("${ai.service.url}") String serviceUrl,
            @Value("${ai.service.internal-key}") String internalKey,
            ObjectMapper objectMapper) {

        this.serviceUrl = serviceUrl;
        this.internalKey = internalKey;
        this.objectMapper = objectMapper;

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public ModelProbeResultVO probe(
            ModelProbeRequest request,
            String resolvedApiKey) {

        try {
            Map<String, Object> body = new LinkedHashMap<>();

            body.put("probe_type", request.getProbeType().name());
            body.put("provider", request.getProvider());
            body.put("base_url", request.getBaseUrl());
            body.put("api_key", resolvedApiKey == null ? "" : resolvedApiKey);
            body.put("model_name", request.getModelName());

            String json = objectMapper.writeValueAsString(body);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(
                            serviceUrl + "/internal/model/probe"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .header("X-Internal-Key", internalKey)
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = httpClient.send(
                    httpRequest,
                    HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() != 200) {
                throw new BusinessException(
                        502,
                        "模型探测失败，AI服务返回HTTP "
                                + response.statusCode()
                                + "，请检查Python服务日志"
                );
            }

            JsonNode root = objectMapper.readTree(response.body());

            ModelProbeResultVO result =
                    new ModelProbeResultVO();

            result.setReachable(
                    root.path("reachable").asBoolean(false));

            result.setProbeType(
                    root.path("probe_type").asString());

            result.setModelName(
                    root.path("model_name").asString());

            JsonNode dim = root.get("actual_dim");

            result.setActualDim(
                    dim == null || dim.isNull()
                            ? null
                            : dim.asInt()
            );

            result.setLatencyMs(
                    root.path("latency_ms").asLong()
            );

            return result;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "模型探测被中断", e);

        } catch (BusinessException e) {
            throw e;

        } catch (Exception e) {
            throw new IllegalStateException(
                    "调用AI模型探测服务失败", e);
        }
    }
}
