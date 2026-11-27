package com.edu.ai.exercise.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record StudentRetryDraftSaveRequest(

        @NotEmpty(message = "答案列表不能为空")
        List<@Valid AnswerRequest> answers

) {

    public record AnswerRequest(

            @NotNull(message = "重练题目ID不能为空")
            Long retryItemId,

            String answer

    ) {}
}
