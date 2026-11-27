package com.edu.ai.exercise.dto;

import java.util.List;

public record StudentMistakeSummaryVO(
        Integer totalMistakes,
        Integer knowledgePointCount,
        List<KnowledgePointGroup> knowledgePoints
) {

    public record KnowledgePointGroup(
            String knowledgePointId,
            String knowledgePointTitle,
            Integer mistakeCount,
            List<MistakeItem> mistakes
    ) {}

    public record MistakeItem(
            String answerId,
            String attemptId,
            String exerciseId,
            String exerciseTitle,
            String courseId,
            String courseName,

            // 知识点信息
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
            java.math.BigDecimal maxScore,
            java.math.BigDecimal earnedScore,
            java.time.LocalDateTime answeredAt
    ) {}
}
