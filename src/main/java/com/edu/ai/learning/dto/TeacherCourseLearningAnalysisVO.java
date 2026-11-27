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

        List<MaterialUsage> materials,

        List<ChapterProgress> chapters,

        List<ChapterProgress> lowCompletionChapters,

        LocalDateTime generatedAt

) {

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
}
