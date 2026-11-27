package com.edu.ai.exercise.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ExerciseVO(
        String id,
        String courseId,
        String courseName,
        String title,
        String description,
        Integer difficulty,
        Integer estimatedMinutes,
        String status,
        String createdBy,
        LocalDateTime publishedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Integer questionCount,
        List<ExerciseQuestionVO> questions
) {}
