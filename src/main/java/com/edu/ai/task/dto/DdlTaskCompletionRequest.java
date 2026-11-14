package com.edu.ai.task.dto;

import jakarta.validation.constraints.NotNull;

public record DdlTaskCompletionRequest(
        @NotNull Boolean completed
) {
}
