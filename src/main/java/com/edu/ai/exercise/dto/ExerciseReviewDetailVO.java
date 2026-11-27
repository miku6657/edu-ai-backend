package com.edu.ai.exercise.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record ExerciseReviewDetailVO(
        String attemptId,
        String exerciseId,
        String exerciseTitle,
        String studentId,
        String studentName,
        Integer attemptNo,
        String status,
        BigDecimal totalScore,
        BigDecimal earnedScore,
        LocalDateTime submittedAt,
        List<Answer> answers
) {
    public record Answer(
            String answerId,
            String questionId,
            String knowledgePointTitle,
            String questionType,
            String stem,
            String studentAnswer,
            String correctAnswer,
            String explanation,
            BigDecimal maxScore,
            BigDecimal earnedScore,
            String teacherFeedback,
            boolean pendingReview
    ) {}
}
