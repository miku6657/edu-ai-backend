package com.edu.ai.exercise.dto;

import java.time.LocalDateTime;
import java.util.List;

public record StudentExerciseRecommendationVO(

        Integer weakKnowledgePointCount,

        Integer recommendedExerciseCount,

        List<Recommendation> recommendations

) {

    public record Recommendation(

            String exerciseId,

            String courseId,

            String courseName,

            String title,

            String description,

            Integer difficulty,

            Integer estimatedMinutes,

            Integer questionCount,

            Integer relevanceScore,

            String recommendationLevel,

            String recommendationReason,

            List<KnowledgePointMatch> matchedKnowledgePoints

    ) {}

    public record KnowledgePointMatch(

            String knowledgePointId,

            String title,

            Integer masteryRate,

            String masteryStatus,

            Integer targetDifficulty,

            String recommendationReason

    ) {}
}
