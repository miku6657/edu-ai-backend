package com.edu.ai.task.service;

import com.edu.ai.task.dto.DdlExtractRequest;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
public class DdlRequestHashService {

    public String sha256(String content) {
        try {
            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(
                    content.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(hash);

        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(
                    "SHA-256算法不可用", e
            );
        }
    }

    /**
     * 对完整业务请求计算 SHA-256 摘要。
     */
    public String hashRequest(DdlExtractRequest request) {

        StringBuilder canonical = new StringBuilder();

        appendField(canonical, request.getSourceText());
        appendField(canonical, request.getCourseId());
        appendField(
                canonical,
                Boolean.TRUE.equals(request.getForceCreate())
        );
        appendField(canonical, request.getReferenceTime());

        return sha256(canonical.toString());
    }

    private void appendField(
            StringBuilder builder,
            Object value) {

        if (value == null) {
            builder.append("-1:");
            return;
        }

        String text = value.toString();

        builder.append(text.length())
                .append(":")
                .append(text);
    }
}
