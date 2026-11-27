package com.edu.ai.exercise.dto;

import java.time.LocalDateTime;
import java.util.List;

public record StudentRetryMasteryVO(

        Integer totalKnowledgePoints,

        Integer masteredKnowledgePoints,

        Integer needsPracticeKnowledgePoints,

        List<KnowledgePoint> knowledgePoints

) {

    public record KnowledgePoint(

            String courseId,

            String courseName,

            String knowledgePointId,

            String knowledgePointTitle,

            Integer questionCount,

            Integer masteredCount,

            Integer needsPracticeCount,

            Integer masteryRate,

            String status,

            LocalDateTime lastPracticedAt

    ) {}
}
