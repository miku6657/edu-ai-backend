package com.edu.ai.system.service;

import com.edu.ai.common.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Set;

@Component
public class ModelConfigValidator {

    private static final Set<String> CHAT_PROVIDERS =
            Set.of(
                    "OPENAI_COMPATIBLE",
                    "DEEPSEEK",
                    "ZHIPU",
                    "OLLAMA"
            );

    private static final Set<String> EMBEDDING_PROVIDERS =
            Set.of(
                    "OPENAI_COMPATIBLE",
                    "ZHIPU",
                    "OLLAMA"
            );

    private final Set<String> ollamaAllowedHosts;

    public ModelConfigValidator(
            @Value("${ai.model.ollama-allowed-hosts:localhost,127.0.0.1}")
            String allowedHosts) {

        this.ollamaAllowedHosts = Set.of(
                allowedHosts.split(",")
        );
    }

    public void validate(
            String type,
            String provider,
            String baseUrl,
            String modelName,
            String apiKey) {

        Set<String> allowed = "CHAT".equals(type)
                ? CHAT_PROVIDERS
                : EMBEDDING_PROVIDERS;

        if (!allowed.contains(provider)) {
            throw new BusinessException(
                    400, "不支持的模型供应商");
        }

        if (baseUrl == null
                || baseUrl.isBlank()
                || baseUrl.length() > 500) {
            throw new BusinessException(
                    400, "模型地址不合法");
        }

        if (modelName == null
                || modelName.isBlank()
                || modelName.length() > 100) {
            throw new BusinessException(
                    400, "模型名称不合法");
        }

        URI uri;

        try {
            uri = URI.create(baseUrl.trim());
        } catch (Exception e) {
            throw new BusinessException(
                    400, "模型地址格式错误");
        }

        if (uri.getHost() == null
                || uri.getUserInfo() != null
                || uri.getFragment() != null) {

            throw new BusinessException(
                    400, "模型地址不合法");
        }

        if ("OLLAMA".equals(provider)) {
            validateOllama(uri);
        } else {
            validatePublicProvider(uri);
        }

        if (!"OLLAMA".equals(provider)
                && (apiKey == null || apiKey.isBlank())) {

            throw new BusinessException(
                    400, "当前模型必须配置 API Key");
        }
    }

    private void validateOllama(URI uri) {

        if (!"http".equalsIgnoreCase(uri.getScheme())
                && !"https".equalsIgnoreCase(uri.getScheme())) {

            throw new BusinessException(
                    400, "Ollama地址协议不合法");
        }

        String host = uri.getHost().toLowerCase();

        if (!ollamaAllowedHosts.contains(host)) {
            throw new BusinessException(
                    400,
                    "Ollama地址不在管理员允许的地址范围内"
            );
        }

        if (uri.getPort() != -1
                && uri.getPort() != 11434) {

            throw new BusinessException(
                    400, "Ollama端口不合法");
        }
    }

    private void validatePublicProvider(URI uri) {

        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new BusinessException(
                    400, "公网模型服务必须使用HTTPS");
        }

        if (uri.getPort() != -1 && uri.getPort() != 443) {
            throw new BusinessException(
                    400, "公网模型服务端口不合法");
        }
    }
}
