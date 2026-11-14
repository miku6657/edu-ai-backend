package com.edu.ai.system.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ModelConfigUpdateRequest {

    @NotBlank
    @JsonProperty("chat_provider")
    private String chatProvider;

    @NotBlank
    @Size(max = 500)
    @JsonProperty("chat_base_url")
    private String chatBaseUrl;

    @Size(max = 1000)
    @JsonProperty("chat_api_key")
    private String chatApiKey;

    @NotBlank
    @Size(max = 100)
    @JsonProperty("chat_model")
    private String chatModel;

    @NotBlank
    @JsonProperty("default_embedding_provider")
    private String defaultEmbeddingProvider;

    @NotBlank
    @Size(max = 500)
    @JsonProperty("embedding_base_url")
    private String embeddingBaseUrl;

    @JsonProperty("reuse_chat_api_key")
    private boolean reuseChatApiKey;

    @Size(max = 1000)
    @JsonProperty("embedding_api_key")
    private String embeddingApiKey;

    @NotBlank
    @Size(max = 100)
    @JsonProperty("default_embedding_model")
    private String defaultEmbeddingModel;

    @JsonProperty("migrate_existing_courses")
    private boolean migrateExistingCourses;
}
