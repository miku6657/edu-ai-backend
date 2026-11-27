package com.edu.ai.learning.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * F6-03：学习报告列表摘要。
 */
public record StudentLearningReportVO(

        String id,

        String studentId,

        String studentName,

        String courseId,

        String courseName,

        String title,

        LocalDate periodStart,

        LocalDate periodEnd,

        long exerciseCount,

        long objectiveAnswerCount,

        long correctObjectiveCount,

        BigDecimal exerciseAccuracyRate,

        long questionCount,

        long completedTaskCount,

        long totalTaskCount,

        int weakPointCount,

        LocalDateTime createdAt

) {}
