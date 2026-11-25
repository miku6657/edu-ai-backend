package com.edu.ai.document.dto;

/**
 * 知识切片与知识点关联信息。
 */
public record ChunkKnowledgeRelationVO(

        String id,

        String chunkId,

        String pointId,

        String pointTitle,

        String chapterId,

        String chapterName,

        int difficulty,

        int matchScore,

        String relationSource,

        String confirmStatus,

        String matchReason,

        String confirmedBy,

        String confirmedTime

) {}
