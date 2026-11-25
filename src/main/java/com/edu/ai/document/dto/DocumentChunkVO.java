package com.edu.ai.document.dto;

public record DocumentChunkVO(

        String id,

        String courseId,

        String documentId,

        String chunkKey,

        int pageNo,

        int chunkIndex,

        String chapterId,

        String chapterName,

        String sectionTitle,

        String content,

        String contentHash,

        int charCount,

        // F2-03新增字段
        String knowledgeStatus,

        int confirmedPointCount,

        int pendingPointCount

) {}
