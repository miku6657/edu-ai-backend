package com.edu.ai.exercise.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record StudentMistakeRetryCreateRequest(

        @NotEmpty(message = "请选择需要重练的错题")
        @Size(max = 100, message = "一次最多选择100道错题")
        List<@NotNull Long> answerIds,

        @Size(max = 100, message = "清单名称最多100字")
        String title

) {}
