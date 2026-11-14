package com.edu.ai.system.dto;

import com.edu.ai.system.entity.User;
import com.edu.ai.system.entity.UserConfig;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

import java.time.LocalDateTime;

@Data
public class UserConfigVO {

    @JsonProperty("user_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    private String username;

    @JsonProperty("chat_provider")
    private String chatProvider;

    @JsonProperty("chat_base_url")
    private String chatBaseUrl;

    @JsonProperty("has_chat_api_key")
    private boolean hasChatApiKey;

    @JsonProperty("chat_api_key_masked")
    private String chatApiKeyMasked;

    @JsonProperty("chat_model")
    private String chatModel;

    @JsonProperty("default_embedding_provider")
    private String defaultEmbeddingProvider;

    @JsonProperty("embedding_base_url")
    private String embeddingBaseUrl;

    @JsonProperty("has_embedding_api_key")
    private boolean hasEmbeddingApiKey;

    @JsonProperty("embedding_api_key_masked")
    private String embeddingApiKeyMasked;

    @JsonProperty("default_embedding_model")
    private String defaultEmbeddingModel;

    @JsonProperty("default_embedding_dim")
    private Integer defaultEmbeddingDim;

    @JsonProperty("has_byok_config")
    private boolean hasByokConfig;

    @JsonProperty("platform_embedding")
    private boolean platformEmbedding;

    @JsonProperty("qqbot_app_id")
    private String qqbotAppId;

    @JsonProperty("has_qqbot_secret")
    private boolean hasQqbotSecret;

    @JsonProperty("qqbot_status")
    private String qqbotStatus;

    @JsonProperty("qqbot_last_error")
    private String qqbotLastError;

    @JsonProperty("qqbot_bound")
    private boolean qqbotBound;

    private String timezone;

    @JsonProperty("dnd_start_time")
    private String dndStartTime;

    @JsonProperty("dnd_end_time")
    private String dndEndTime;

    @JsonProperty("bound_im_platform")
    private String boundImPlatform;

    @JsonProperty("bound_im_uid")
    private String boundImUid;

    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;

    @JsonProperty("existing_course_count")
    private long existingCourseCount;

    public static UserConfigVO from(
            User user,
            UserConfig config,
            long courseCount) {

        UserConfigVO vo = new UserConfigVO();

        vo.setUserId(user.getId());
        vo.setUsername(user.getUsername());

        vo.setChatProvider(config.getChatProvider());
        vo.setChatBaseUrl(config.getChatBaseUrl());
        vo.setChatModel(config.getChatModel());

        boolean hasChatKey = hasText(
                config.getChatApiKeyCiphertext());

        vo.setHasChatApiKey(hasChatKey);
        vo.setChatApiKeyMasked(
                hasChatKey ? "已安全保存" : null);

        vo.setDefaultEmbeddingProvider(
                config.getEmbeddingProvider());

        vo.setEmbeddingBaseUrl(
                config.getEmbeddingBaseUrl());

        vo.setDefaultEmbeddingModel(
                config.getEmbeddingModel());

        vo.setDefaultEmbeddingDim(
                config.getEmbeddingDim());

        boolean hasEmbeddingKey = hasText(
                config.getEmbeddingApiKeyCiphertext());

        vo.setHasEmbeddingApiKey(hasEmbeddingKey);
        vo.setEmbeddingApiKeyMasked(
                hasEmbeddingKey ? "已安全保存" : null);

        // 这里只反映数据库已有配置，不声称模型实际可用。
        boolean chatConfigured =
                hasText(config.getChatProvider())
                && hasText(config.getChatBaseUrl())
                && hasText(config.getChatModel())
                && (hasChatKey
                    || "OLLAMA".equals(config.getChatProvider()));

        vo.setHasByokConfig(chatConfigured);

        // 平台 Embedding 配置尚未实现。
        vo.setPlatformEmbedding(false);

        vo.setQqbotAppId(config.getQqbotAppId());
        vo.setHasQqbotSecret(
                hasText(config.getQqbotSecretCiphertext()));
        vo.setQqbotStatus(
                config.getQqbotStatus() == null
                    ? "DISCONNECTED"
                    : config.getQqbotStatus());
        vo.setQqbotLastError(config.getQqbotLastError());
        vo.setQqbotBound(
                Boolean.TRUE.equals(config.getQqbotBound()));

        vo.setTimezone(config.getTimezone());

        vo.setDndStartTime(
                config.getDndStartTime() == null
                    ? "23:30:00"
                    : config.getDndStartTime().format(
                            java.time.format.DateTimeFormatter
                                    .ofPattern("HH:mm:ss")
                    )
        );

        vo.setDndEndTime(
                config.getDndEndTime() == null
                    ? "07:30:00"
                    : config.getDndEndTime().format(
                            java.time.format.DateTimeFormatter
                                    .ofPattern("HH:mm:ss")
                    )
        );

        vo.setBoundImPlatform(
                config.getBoundImPlatform());
        vo.setBoundImUid(
                config.getBoundImUid());

        vo.setUpdatedAt(config.getUpdateTime());
        vo.setExistingCourseCount(courseCount);

        return vo;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
