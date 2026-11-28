package com.edu.ai.audit.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record ContentOperationOverviewVO(

        String courseId,

        LocalDate startDate,

        LocalDate endDate,

        DocumentStats documents,

        QualityStats quality,

        HotQuestionStats hotQuestions,

        UsageStats usage,

        List<OperationTypeStat> operationTypes,

        List<DailyUsage> dailyUsage,

        LocalDateTime generatedAt

) {

    public record DocumentStats(
            long publishedCount,
            long expiringCount,
            long expiredCount,
            long suspendedCount,
            long confirmedSummaryCount
    ) {}

    public record QualityStats(
            long openIssueCount,
            long resolvedIssueCount
    ) {}

    public record HotQuestionStats(
            long draftCount,
            long pendingReviewCount,
            long publishedCount,
            long rejectedCount,
            long offlineCount
    ) {}

    public record UsageStats(
            long previewCount,
            long downloadCount,
            long materialStudentCount,
            long questionCount,
            long questionStudentCount,
            long recommendationExposureCount,
            long recommendationClickCount,
            BigDecimal recommendationClickRate,
            long operationCount
    ) {}

    public record OperationTypeStat(
            String operationType,
            String operationName,
            long operationCount
    ) {}

    public record DailyUsage(
            LocalDate date,
            long previewCount,
            long downloadCount,
            long questionCount,
            long recommendationExposureCount,
            long recommendationClickCount,
            long operationCount
    ) {}
}
