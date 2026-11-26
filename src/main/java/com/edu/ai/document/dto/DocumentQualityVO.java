package com.edu.ai.document.dto;

import java.time.LocalDateTime;
import java.util.List;

public final class DocumentQualityVO {

    private DocumentQualityVO() {
    }

    /**
     * 抽检任务概要。
     */
    public record Task(

            String id,

            String courseId,

            String documentId,

            String resourceId,

            Integer versionNo,

            String documentName,

            String title,

            Integer sampleRate,

            Integer totalChunks,

            Integer sampleCount,

            String status,

            String creatorId,

            String completedBy,

            LocalDateTime completedTime,

            LocalDateTime createTime,

            LocalDateTime updateTime

    ) {}

    /**
     * 抽检样本。
     */
    public record Sample(

            String id,

            String taskId,

            String chunkId,

            Integer pageNo,

            Integer chunkIndex,

            String sectionTitle,

            String content,

            String contentHash,

            Integer duplicateCount,

            String status,

            String checkComment,

            String checkerId,

            LocalDateTime checkedTime

    ) {}

    /**
     * 质量问题。
     */
    public record Issue(

            String id,

            String taskId,

            String sampleId,

            String issueType,

            String description,

            String reporterId,

            String assigneeId,

            String status,

            String resolution,

            String replacementDocumentId,

            LocalDateTime resolvedTime,

            String reviewerId,

            String reviewComment,

            LocalDateTime reviewedTime,

            LocalDateTime createTime

    ) {}

    /**
     * 抽检进度。
     */
    public record Progress(

            int totalSamples,

            int pendingSamples,

            int passedSamples,

            int failedSamples,

            int checkedSamples,

            int checkPercent,

            double passRate,

            int totalIssues,

            int openIssues,

            int resolvedIssues,

            int closedIssues,

            boolean canComplete

    ) {}

    /**
     * 抽检任务详情。
     */
    public record Detail(

            Task task,

            Progress progress,

            List<Sample> samples,

            List<Issue> issues

    ) {}

    /**
     * 可分配责任人。
     */
    public record Assignee(

            String id,

            String username,

            String role

    ) {}

    /**
     * 文档质量控制状态。
     */
    public record Control(

            String documentId,

            String qualityStatus,

            String qualityBlockReason,

            String qualityBlockedBy,

            LocalDateTime qualityBlockedAt,

            boolean retrievable

    ) {}
}
