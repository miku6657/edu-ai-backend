package com.edu.ai.exercise.dto;

import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;

public record ExerciseQuestionVO(
        String id,
        String knowledgePointId,
        String knowledgePointTitle,
        String questionType,
        String stem,
        JsonNode options,
        String correctAnswer,
        String explanation,
        BigDecimal score,
        Integer sortOrder
) {}
