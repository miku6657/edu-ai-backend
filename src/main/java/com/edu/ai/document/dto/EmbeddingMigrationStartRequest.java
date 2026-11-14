package com.edu.ai.document.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class EmbeddingMigrationStartRequest {

    @NotNull(message = "课程ID不能为空")
    private Long courseId;
}
