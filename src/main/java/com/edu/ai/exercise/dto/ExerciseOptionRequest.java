package com.edu.ai.exercise.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ExerciseOptionRequest(
        @NotBlank(message = "选项标识不能为空")
        @Size(max = 8, message = "选项标识最多8个字符")
        String label,

        @NotBlank(message = "选项内容不能为空")
        @Size(max = 1000, message = "选项内容最多1000字")
        String content
) {}
