package com.edu.ai.document.service;

import com.edu.ai.system.security.ConfigCryptoService;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Component
public class EmbeddingConfigFingerprint {

    private final ConfigCryptoService cryptoService;

    public EmbeddingConfigFingerprint(
            ConfigCryptoService cryptoService) {
        this.cryptoService = cryptoService;
    }

    public String calculate(
            String provider,
            String baseUrl,
            String model,
            String apiKey) {

        String keyFingerprint =
                cryptoService.fingerprint(
                        apiKey == null ? "" : apiKey
                );

        String value = String.join(
                "\n",
                normalize(provider),
                normalize(baseUrl),
                normalize(model),
                keyFingerprint == null ? "" : keyFingerprint
        );

        try {
            byte[] digest = MessageDigest
                    .getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));

            return HexFormat.of().formatHex(digest);

        } catch (Exception e) {
            throw new IllegalStateException(
                    "Embedding配置指纹计算失败", e);
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
