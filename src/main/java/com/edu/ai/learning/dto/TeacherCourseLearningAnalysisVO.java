package com.edu.ai.learning.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record TeacherCourseLearningAnalysisVO(

        String courseId,

        String courseName,

        long studentCount,

        long activeTaskCount,

        long taskAssignmentCount,

        long completedTaskAssignmentCount,

        BigDecimal taskCompletionRate,

        long publishedExerciseCount,

        long submittedAttemptCount,

        long participatingStudentCount,

        BigDecimal exerciseParticipationRate,

        MaterialSummary materialSummary,

        List<KnowledgePointLearningStat> knowledgePoints,

        List<StudentProgress> students,

        List<MaterialUsage> materials,

        List<ChapterProgress> chapters,

        List<ChapterProgress> lowCompletionChapters,

        RecommendationUsage recommendationUsage,

        LocalDateTime generatedAt

) {

    public record StudentProgress(
            String studentId,
            String studentName,
            String username,

            long submittedAttemptCount,

            long objectiveAnswerCount,
            long correctObjectiveCount,
            BigDecimal exerciseAccuracyRate,

            long questionCount,

            long completedTaskCount,
            long totalTaskCount,
            BigDecimal taskCompletionRate,

            long materialAccessCount,
            LocalDateTime lastActiveAt,

            // 新增：学习状态
            String learningStatus,

            // 新增：预警原因
            List<String> statusReasons
    ) {}

    public record MaterialSummary(
            long documentCount,
            long accessedDocumentCount,
            long accessCount,
            long previewCount,
            long downloadCount,
            long activeStudentCount
    ) {}

    public record MaterialUsage(
            String documentId,
            String documentName,
            String resourceType,
            String chapterNames,
            long previewCount,
            long downloadCount,
            long totalAccessCount,
            long studentCount,
            LocalDateTime lastAccessAt
    ) {}

    public record KnowledgePointLearningStat(

            String pointId,

            String title,

            long questionCount,

            long questionStudentCount,

            long practiceQuestionCount,

            long masteredQuestionCount,

            BigDecimal practiceMasteryRate,

            long practiceStudentCount,

            long selfAssessmentCount,

            BigDecimal averageSelfAssessmentLevel,

            LocalDateTime latestAskedAt,

            LocalDateTime lastPracticedAt

    ) {}

    public record ChapterProgress(
            String chapterId,
            String chapterName,
            long questionCount,
            long studentCount,
            long totalExpectedAnswers,
            long completedStudentQuestionCount,
            BigDecimal completionRate,
            boolean lowCompletion
    ) {}

    /**
     * 近30天练习推荐使用情况。
     */
    public record RecommendationUsage(

            int periodDays,

            long exposureCount,

            long clickCount,

            long exposedStudentCount,

            long clickedStudentCount,

            BigDecimal clickRate,

            List<RecommendationExerciseUsage> topExercises

    ) {}

    /**
     * 推荐练习使用排行。
     */
    public record RecommendationExerciseUsage(

            String exerciseId,

            String exerciseTitle,

            long exposureCount,

            long clickCount,

            long exposedStudentCount,

            long clickedStudentCount,

            BigDecimal clickRate

    ) {}
}
