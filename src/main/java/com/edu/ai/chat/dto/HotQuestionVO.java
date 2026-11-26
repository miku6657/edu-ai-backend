package com.edu.ai.chat.dto;

public record HotQuestionVO(
        String questionText,
        String knowledgePointId,
        String knowledgePointTitle,
        long questionCount,
        long studentCount,
        String lastAskedAt
) {
}
