package com.edu.ai.exercise.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record StudentMistakeDetailVO(
        String answerId,
        String attemptId,
        String exerciseId,
        String exerciseTitle,
        String courseId,
        String courseName,
        String knowledgePointId,
        String knowledgePointTitle,
        String questionId,
        String questionType,
        String stem,
        String studentAnswer,
        String correctAnswer,
        String explanation,
        String mistakeType,
        String teacherFeedback,
        BigDecimal maxScore,
        BigDecimal earnedScore,
        LocalDateTime answeredAt
) {}
