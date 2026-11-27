package com.edu.ai.learning.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * F6-03：学习报告详情。
 *
 * 包含：
 * 1. 阶段统计数据
 * 2. 学习总结
 * 3. 改进建议
 * 4. 薄弱知识点快照
 * 5. 教师批注历史
 */
public record StudentLearningReportDetailVO(

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

        String reportSummary,

        String improvementSuggestions,

        LocalDateTime createdAt,

        List<WeakPointSnapshotVO> weakPoints,

        List<CommentVO> comments

) {

    /**
     * 报告生成时的薄弱知识点快照。
     */
    public record WeakPointSnapshotVO(

            String pointId,

            String title,

            int effectiveQuestionCount,

            int questionSessionCount,

            int questionInferredLevel,

            int practiceQuestionCount,

            int masteredQuestionCount,

            Integer practiceMasteryRate,

            int diagnosisLevel,

            String diagnosisLabel,

            String diagnosisReason,

            String suggestion

    ) {}

    /**
     * 教师批注。
     */
    public record CommentVO(

            String id,

            String teacherId,

            String teacherName,

            String content,

            LocalDateTime createdAt

    ) {}
}
