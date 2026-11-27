package com.edu.ai.exercise.dto;

import java.time.LocalDateTime;

public record StudentMistakeRetryListVO(
        String id,
        String title,
        Integer itemCount,
        LocalDateTime createdAt
) {}
