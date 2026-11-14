package com.edu.ai.system.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class ConfigCryptoService {

    private static final String AES_ALGORITHM =
            "AES/GCM/NoPadding";

    private static final String HMAC_ALGORITHM =
            "HmacSHA256";

    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH = 128;

    private final SecretKeySpec aesKey;
    private final SecretKeySpec hmacKey;

    private final SecureRandom random = new SecureRandom();

    public ConfigCryptoService(
            @Value("${app.security.config-encryption-key}")
            String encryptionKey,
            @Value("${app.security.config-fingerprint-key}")
            String fingerprintKey) {

        byte[] aesBytes = Base64.getDecoder()
                .decode(encryptionKey);

        byte[] hmacBytes = Base64.getDecoder()
                .decode(fingerprintKey);

        if (aesBytes.length != 32) {
            throw new IllegalArgumentException(
                    "AES密钥必须为32字节"
            );
        }

        if (hmacBytes.length != 32) {
            throw new IllegalArgumentException(
                    "HMAC密钥必须为32字节"
            );
        }

        this.aesKey = new SecretKeySpec(
                aesBytes, "AES");

        this.hmacKey = new SecretKeySpec(
                hmacBytes, HMAC_ALGORITHM);
    }

    /**
     * AES-256-GCM 加密。
     */
    public String encrypt(String plaintext) {

        if (plaintext == null || plaintext.isBlank()) {
            return null;
        }

        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(
                    AES_ALGORITHM);

            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    aesKey,
                    new GCMParameterSpec(TAG_LENGTH, iv)
            );

            byte[] encrypted = cipher.doFinal(
                    plaintext.getBytes(StandardCharsets.UTF_8)
            );

            ByteBuffer buffer = ByteBuffer.allocate(
                    iv.length + encrypted.length);

            buffer.put(iv);
            buffer.put(encrypted);

            return Base64.getEncoder().encodeToString(
                    buffer.array());

        } catch (Exception e) {
            throw new IllegalStateException(
                    "配置加密失败", e);
        }
    }

    /**
     * AES-256-GCM 解密。
     */
    public String decrypt(String ciphertext) {

        if (ciphertext == null || ciphertext.isBlank()) {
            return null;
        }

        try {
            byte[] data = Base64.getDecoder()
                    .decode(ciphertext);

            if (data.length <= IV_LENGTH + 16) {
                throw new IllegalArgumentException(
                        "密文格式不正确");
            }

            ByteBuffer buffer = ByteBuffer.wrap(data);

            byte[] iv = new byte[IV_LENGTH];
            buffer.get(iv);

            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);

            Cipher cipher = Cipher.getInstance(
                    AES_ALGORITHM);

            cipher.init(
                    Cipher.DECRYPT_MODE,
                    aesKey,
                    new GCMParameterSpec(TAG_LENGTH, iv)
            );

            byte[] plaintext = cipher.doFinal(encrypted);

            return new String(
                    plaintext,
                    StandardCharsets.UTF_8);

        } catch (Exception e) {
            throw new IllegalStateException(
                    "配置解密失败，请检查密钥或密文完整性",
                    e);
        }
    }

    /**
     * 使用独立密钥计算稳定指纹。
     */
    public String fingerprint(String plaintext) {

        if (plaintext == null || plaintext.isBlank()) {
            return null;
        }

        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(hmacKey);

            byte[] digest = mac.doFinal(
                    plaintext.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(digest);

        } catch (Exception e) {
            throw new IllegalStateException(
                    "API Key指纹计算失败", e);
        }
    }

    /**
     * 恒定时间比较指纹。
     */
    public boolean matches(
            String fingerprint,
            String plaintext) {

        if (fingerprint == null || plaintext == null) {
            return false;
        }

        String current = fingerprint(plaintext);

        return MessageDigest.isEqual(
                fingerprint.getBytes(StandardCharsets.UTF_8),
                current.getBytes(StandardCharsets.UTF_8)
        );
    }

    public String mask(String value) {

        if (value == null || value.isBlank()) {
            return null;
        }

        if (value.length() <= 8) {
            return "********";
        }

        return value.substring(0, 4)
                + "********"
                + value.substring(value.length() - 4);
    }
}
