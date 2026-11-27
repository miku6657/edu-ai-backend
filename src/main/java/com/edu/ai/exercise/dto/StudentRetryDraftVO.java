package com.edu.ai.exercise.dto;

import java.util.List;

public record StudentRetryDraftVO(
        String attemptId,
        List<Answer> answers
) {

    public record Answer(
            String retryItemId,
            String answer
    ) {}
}
