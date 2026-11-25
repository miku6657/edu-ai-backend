package com.edu.ai.document.dto;

/**
 * 批量生成知识点推荐结果的统计信息。
 */
public record ChunkKnowledgeRecommendVO(

        int processedChunks,

        int recommendedChunks,

        int unmatchedChunks,

        int skippedChunks,

        int generatedRelations

) {}
