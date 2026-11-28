package com.edu.ai.audit.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ContentOperationLogPageVO(

        long current,

        long size,

        long total,

        long pages,

        List<Item> records

) {

    public record Item(

            String id,

            String courseId,

            String courseName,

            String documentId,

            String documentName,

            String operatorId,

            String operatorName,

            String operationType,

            String operationName,

            String description,

            String beforeValue,

            String afterValue,

            LocalDateTime createTime

    ) {}
}
