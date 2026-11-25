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

        int charCount

) {}
