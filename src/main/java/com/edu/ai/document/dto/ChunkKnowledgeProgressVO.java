package com.edu.ai.document.dto;

public record ChunkKnowledgeProgressVO(

        String documentId,

        long totalChunks,

        long unprocessedChunks,

        long recommendedChunks,

        long confirmedChunks,

        long notApplicableChunks,

        long pendingRelations,

        long confirmedRelations,

        int completionPercent,

        boolean completed

) {}
