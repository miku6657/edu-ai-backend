package com.edu.ai.document.dto;

import com.edu.ai.document.entity.DocumentReviewRecord;

import java.time.LocalDateTime;

public record DocumentReviewRecordVO(

        String id,

        String operatorId,

        String action,

        String previousStatus,

        String nextStatus,

        String comment,

        LocalDateTime createTime

) {

    public static DocumentReviewRecordVO from(
            DocumentReviewRecord record) {

        return new DocumentReviewRecordVO(

                record.getId().toString(),

                record.getOperatorId().toString(),

                record.getAction(),

                record.getPreviousStatus(),

                record.getNextStatus(),

                record.getComment(),

                record.getCreateTime()
        );
    }
}
