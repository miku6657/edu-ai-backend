package com.edu.ai.system.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ModelProbeRequest {

    @NotNull
    @JsonProperty("probe_type")
    private ProbeType probeType;

    @NotBlank
    private String provider;

    @NotBlank
    @Size(max = 500)
    @JsonProperty("base_url")
    private String baseUrl;

    @Size(max = 1000)
    @JsonProperty("api_key")
    private String apiKey;

    @JsonProperty("use_saved_key")
    private boolean useSavedKey;

    @JsonProperty("reuse_chat_api_key")
    private boolean reuseChatApiKey;

    @NotBlank
    @Size(max = 100)
    @JsonProperty("model_name")
    private String modelName;

    public enum ProbeType {
        CHAT,
        EMBEDDING
    }
}
