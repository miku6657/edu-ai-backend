package com.edu.ai.exercise.dto;

import java.time.LocalDateTime;
import java.util.List;

public record TeacherExerciseExplainAnalyticsVO(

        Long sessionCount,

        Long studentCount,

        Long questionCount,

        Long requestCount,

        Long followUpCount,

        LocalDateTime latestActivityAt,

        List<KnowledgePointStat> knowledgePoints,

        List<HotQuestionStat> hotQuestions

) {

    public record KnowledgePointStat(

            String pointId,

            String title,

            Long sessionCount,

            Long studentCount,

            Long requestCount,

            LocalDateTime latestActivityAt

    ) {}

    public record HotQuestionStat(

            String questionId,

            String stem,

            String knowledgePointTitle,

            Long sessionCount,

            Long studentCount,

            Long requestCount,

            LocalDateTime latestActivityAt

    ) {}
}
