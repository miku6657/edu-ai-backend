package com.edu.ai.exercise.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record StudentRetrySelfReviewRequest(

        @NotEmpty(message = "请完成简答题自我核对")
        List<@Valid ReviewItem> answers

) {

    public record ReviewItem(

            @NotNull(message = "重练题目ID不能为空")
            Long retryItemId,

            @NotNull(message = "请选择掌握情况")
            Boolean understood

    ) {}
}
