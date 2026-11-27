package com.edu.ai.exercise.dto;

import java.math.BigDecimal;
import java.util.List;

public record StudentRetryDetailVO(
        String id,
        String title,
        Integer itemCount,
        List<Item> items
) {

    public record Item(
            String id,
            String sourceAnswerId,
            String questionId,
            String courseName,
            String exerciseTitle,
            String knowledgePointTitle,
            String questionType,
            String stem,
            List<Option> options,
            BigDecimal score,
            Integer sortOrder
    ) {}

    public record Option(
            String label,
            String content
    ) {}
}
