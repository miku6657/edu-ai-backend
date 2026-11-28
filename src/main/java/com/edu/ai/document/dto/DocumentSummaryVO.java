package com.edu.ai.document.dto;

import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;

public record DocumentSummaryVO(

        String id,

        String courseId,

        String documentId,

        Integer revision,

        String summary,

        JsonNode outline,

        String status,

        Integer sourceChunkCount,

        String generatedBy,

        LocalDateTime generatedAt,

        String confirmedBy,

        LocalDateTime confirmedAt

) {}
