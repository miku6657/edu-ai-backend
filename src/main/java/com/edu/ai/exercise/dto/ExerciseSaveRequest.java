package com.edu.ai.exercise.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ExerciseSaveRequest(
        @NotBlank(message = "练习名称不能为空")
        @Size(max = 160, message = "练习名称最多160字")
        String title,

        @Size(max = 1000, message = "练习说明最多1000字")
        String description,

        @NotNull(message = "请选择练习难度")
        @Min(value = 1, message = "难度值不正确")
        @Max(value = 3, message = "难度值不正确")
        Integer difficulty,

        @NotNull(message = "请填写预计用时")
        @Min(value = 1, message = "预计用时至少1分钟")
        @Max(value = 240, message = "预计用时不能超过240分钟")
        Integer estimatedMinutes,

        @NotEmpty(message = "至少添加一道题目")
        @Size(max = 100, message = "一个练习最多100道题")
        @Valid
        List<ExerciseQuestionRequest> questions
) {}
