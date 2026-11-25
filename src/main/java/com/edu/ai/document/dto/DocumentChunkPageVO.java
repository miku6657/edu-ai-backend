package com.edu.ai.document.dto;

import java.util.List;

public record DocumentChunkPageVO(

        String documentId,

        String documentName,

        String versionStatus,

        int chunkingVersion,

        boolean initialized,

        long totalChunks,

        long classifiedChunks,

        long total,

        int page,

        int pageSize,

        List<DocumentChunkVO> records

) {}
