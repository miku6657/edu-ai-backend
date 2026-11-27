package com.edu.ai.exercise.dto;

import java.time.LocalDateTime;

public record StudentRetryListVO(
        String id,
        String title,
        Integer itemCount,
        String status,
        Integer attemptCount,
        String latestAttemptStatus,
        LocalDateTime createdAt
) {}
