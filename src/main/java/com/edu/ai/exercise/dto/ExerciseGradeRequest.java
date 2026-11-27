package com.edu.ai.exercise.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record ExerciseGradeRequest(

        @NotEmpty(message = "至少提交一道题目的批改结果")
        @Size(max = 100, message = "一次最多批改100道题")
        @Valid
        List<AnswerGrade> answers

) {
    public record AnswerGrade(

            @NotNull(message = "答题记录ID不能为空")
            Long answerId,

            @NotNull(message = "得分不能为空")
            @DecimalMin(value = "0.00", message = "得分不能小于0")
            BigDecimal score,

            @Size(max = 2000, message = "评语最多2000字")
            String feedback

    ) {}
}
