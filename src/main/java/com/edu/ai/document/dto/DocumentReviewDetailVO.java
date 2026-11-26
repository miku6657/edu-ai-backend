package com.edu.ai.document.dto;

import java.time.LocalDateTime;
import java.util.List;

public record DocumentReviewDetailVO(

        String documentId,

        String courseId,

        String resourceId,

        String originalName,

        Integer versionNo,

        String status,

        String versionStatus,

        String reviewStatus,

        String reviewSubmitterId,

        LocalDateTime reviewSubmitTime,

        String reviewerId,

        LocalDateTime reviewTime,

        String reviewComment,

        ChunkKnowledgeProgressVO knowledgeProgress,

        boolean canSubmit,

        boolean canReview,

        boolean canWithdraw,

        boolean published,

        List<DocumentReviewRecordVO> records

) {
}
