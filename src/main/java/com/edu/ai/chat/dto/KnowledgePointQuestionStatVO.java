package com.edu.ai.chat.dto;

public record KnowledgePointQuestionStatVO(
        String pointId,
        String title,
        long questionCount,
        long studentCount,
        String latestAskedAt
) {
}
