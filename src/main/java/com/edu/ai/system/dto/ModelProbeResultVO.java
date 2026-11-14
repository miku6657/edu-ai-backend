package com.edu.ai.system.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class ModelProbeResultVO {

    private boolean reachable;

    @JsonProperty("probe_type")
    private String probeType;

    @JsonProperty("model_name")
    private String modelName;

    @JsonProperty("actual_dim")
    private Integer actualDim;

    @JsonProperty("latency_ms")
    private long latencyMs;
}
