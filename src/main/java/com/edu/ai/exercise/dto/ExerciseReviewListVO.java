package com.edu.ai.exercise.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ExerciseReviewListVO(
        String attemptId,
        String exerciseId,
        String exerciseTitle,
        String courseId,
        String courseName,
        String studentId,
        String studentName,
        Integer attemptNo,
        Integer pendingReviewCount,
        BigDecimal totalScore,
        BigDecimal earnedScore,
        LocalDateTime submittedAt
) {}
