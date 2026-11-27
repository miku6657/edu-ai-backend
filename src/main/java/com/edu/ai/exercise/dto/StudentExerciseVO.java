package com.edu.ai.exercise.dto;

import java.math.BigDecimal;
import java.util.List;

public record StudentExerciseVO(
        String id,
        String courseId,
        String courseName,
        String title,
        String description,
        Integer difficulty,
        Integer estimatedMinutes,
        Integer questionCount,
        Integer attemptCount,
        String latestStatus,
        List<Question> questions
) {

    public record Option(
            String label,
            String content
    ) {}

    public record Question(
            String id,
            String knowledgePointId,
            String knowledgePointTitle,
            String questionType,
            String stem,
            List<Option> options,
            BigDecimal score,
            Integer sortOrder
    ) {}
}
