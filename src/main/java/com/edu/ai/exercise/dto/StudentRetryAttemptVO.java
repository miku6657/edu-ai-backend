package com.edu.ai.exercise.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record StudentRetryAttemptVO(
        String id,
        String retryListId,
        Integer attemptNo,
        String status,
        BigDecimal totalScore,
        BigDecimal earnedScore,
        LocalDateTime startedAt,
        LocalDateTime submittedAt
) {}
