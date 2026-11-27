package com.edu.ai.exercise.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record StudentRetryHistoryVO(

        String attemptId,

        String retryListId,

        String retryListTitle,

        Integer itemCount,

        Integer attemptNo,

        String status,

        BigDecimal totalScore,

        BigDecimal earnedScore,

        Integer answeredCount,

        Integer objectiveCount,

        Integer correctCount,

        Integer accuracyRate,

        Integer shortAnswerCount,

        Integer selfReviewedMasteredCount,

        LocalDateTime startedAt,

        LocalDateTime submittedAt,

        LocalDateTime gradedAt

) {}
