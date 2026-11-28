package com.edu.ai.hotquestion.dto;

import java.time.LocalDateTime;

public record CourseHotQuestionVO(

        String id,

        String courseId,

        String knowledgePointId,

        String knowledgePointTitle,

        String sourceQuestionText,

        String questionText,

        String answerText,

        String category,

        Long questionCount,

        Long studentCount,

        String status,

        String createdBy,

        String updatedBy,

        String reviewedBy,

        String reviewComment,

        LocalDateTime createdAt,

        LocalDateTime updatedAt,

        LocalDateTime submittedAt,

        LocalDateTime reviewedAt,

        LocalDateTime publishedAt,

        LocalDateTime offlinedAt

) {}
