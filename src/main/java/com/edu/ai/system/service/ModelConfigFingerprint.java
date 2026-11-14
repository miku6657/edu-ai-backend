package com.edu.ai.system.service;

import com.edu.ai.system.entity.UserConfig;
import com.edu.ai.system.security.ConfigCryptoService;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class ModelConfigFingerprint {

    private final ConfigCryptoService cryptoService;

    public ModelConfigFingerprint(
            ConfigCryptoService cryptoService) {
        this.cryptoService = cryptoService;
    }

    /**
     * 判断 Embedding 配置是否发生变化。
     */
    public boolean embeddingChanged(
            UserConfig old,
            String provider,
            String baseUrl,
            String model,
            String plaintextApiKey,
            boolean reuseChatKey,
            String chatKeyFingerprint) {

        if (old == null) {
            return true;
        }

        if (!Objects.equals(
                old.getEmbeddingProvider(), provider)) {
            return true;
        }

        if (!Objects.equals(
                normalize(old.getEmbeddingBaseUrl()),
                normalize(baseUrl))) {
            return true;
        }

        if (!Objects.equals(
                normalize(old.getEmbeddingModel()),
                normalize(model))) {
            return true;
        }

        String newFingerprint = plaintextApiKey == null
                ? null
                : cryptoService.fingerprint(plaintextApiKey);

        if (reuseChatKey) {
            newFingerprint = chatKeyFingerprint;
        }

        String oldFingerprint =
                old.getEmbeddingApiKeyFingerprint();

        if (oldFingerprint == null
                && old.getEmbeddingApiKeyCiphertext() != null) {

            String oldKey = cryptoService.decrypt(
                    old.getEmbeddingApiKeyCiphertext());

            oldFingerprint = cryptoService.fingerprint(oldKey);
        }

        return !Objects.equals(
                oldFingerprint,
                newFingerprint
        );
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }
}
