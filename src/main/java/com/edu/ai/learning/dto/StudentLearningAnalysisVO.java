package com.edu.ai.learning.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * F6-01：学生学习记录统计。
 */
public record StudentLearningAnalysisVO(
        long exerciseCount,
        Integer exerciseAccuracyRate,
        long objectiveAnswerCount,
        long questionCount,
        long completedTaskCount,
        long totalTaskCount,
        LocalDate weekStart,
        LocalDate weekEnd,
        List<DailyRecord> weeklyRecords
) {
    /**
     * 本周每日学习活动统计。
     */
    public record DailyRecord(
            LocalDate date,
            long exerciseCount,
            long questionCount,
            long completedTaskCount
    ) {}
}
