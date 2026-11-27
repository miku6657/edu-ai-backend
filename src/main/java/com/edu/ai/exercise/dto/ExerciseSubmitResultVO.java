package com.edu.ai.exercise.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record ExerciseSubmitResultVO(

        String attemptId,

        String exerciseId,

        Integer attemptNo,

        String status,

        BigDecimal totalScore,

        BigDecimal earnedScore,

        Integer pendingReviewCount,

        LocalDateTime submittedAt,

        List<AnswerResult> answers

) {

    public record AnswerResult(

            String questionId,

            String questionType,

            String stem,

            String studentAnswer,

            Boolean correct,

            String correctAnswer,

            String explanation,

            BigDecimal score,

            BigDecimal earnedScore,

            boolean pendingReview,

            String teacherFeedback

    ) {}
}
