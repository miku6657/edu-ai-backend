package com.edu.ai.exercise.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record ExerciseQuestionRequest(
        @NotNull(message = "请选择题目所属知识点")
        Long knowledgePointId,

        @NotBlank(message = "请选择题型")
        String questionType,

        @NotBlank(message = "题目内容不能为空")
        @Size(max = 5000, message = "题目内容最多5000字")
        String stem,

        @Valid
        @Size(max = 8, message = "最多设置8个选项")
        List<ExerciseOptionRequest> options,

        @NotBlank(message = "请填写参考答案")
        @Size(max = 2000, message = "参考答案最多2000字")
        String correctAnswer,

        @Size(max = 5000, message = "题目解析最多5000字")
        String explanation,

        @NotNull(message = "请设置题目分值")
        @DecimalMin(value = "0.5", message = "题目分值至少0.5分")
        BigDecimal score
) {}
