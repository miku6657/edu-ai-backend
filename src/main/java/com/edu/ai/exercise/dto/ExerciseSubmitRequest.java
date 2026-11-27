package com.edu.ai.exercise.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ExerciseSubmitRequest(

        @NotNull(message = "答题列表不能为空")
        @Size(max = 100, message = "答题数量不能超过100")
        @Valid
        List<AnswerRequest> answers

) {

    public record AnswerRequest(

            @NotNull(message = "题目ID不能为空")
            Long questionId,

            @Size(max = 10000, message = "答案内容过长")
            String answer

    ) {}
}
