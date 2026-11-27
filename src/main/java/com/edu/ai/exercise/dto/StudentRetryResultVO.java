package com.edu.ai.exercise.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record StudentRetryResultVO(

        String attemptId,

        String retryListId,

        Integer attemptNo,

        String status,

        BigDecimal totalScore,

        BigDecimal earnedScore,

        Integer pendingSelfReviewCount,

        LocalDateTime submittedAt,

        List<AnswerResult> answers

) {

    public record AnswerResult(

            String retryItemId,

            String questionId,

            String questionType,

            String stem,

            String studentAnswer,

            Boolean correct,

            String correctAnswer,

            String explanation,

            BigDecimal score,

            BigDecimal earnedScore,

            Boolean selfReviewUnderstood,

            boolean pendingSelfReview

    ) {}
}
