package com.edu.ai.task.dto;

import tools.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class DdlExtractResult {

    @JsonProperty("extract_outcome")
    private String extractOutcome;

    private DdlTaskVO task;

    @JsonProperty("change_draft")
    private TaskChangeDraftVO changeDraft;
}
