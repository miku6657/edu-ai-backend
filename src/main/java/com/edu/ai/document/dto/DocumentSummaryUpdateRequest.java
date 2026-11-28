package com.edu.ai.document.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import tools.jackson.databind.JsonNode;

public record DocumentSummaryUpdateRequest(

        @NotBlank(message = "摘要内容不能为空")
        @Size(
                max = 10000,
                message = "摘要内容不能超过10000个字符"
        )
        String summary,

        @NotNull(message = "章节大纲不能为空")
        JsonNode outline

) {}
