package com.edu.ai.document.dto;

import java.util.List;

/**
 * 单个切片的完整知识点关联状态。
 */
public record ChunkKnowledgeDetailVO(

        String chunkId,

        String knowledgeStatus,

        String knowledgeCheckedBy,

        String knowledgeCheckedTime,

        List<ChunkKnowledgeRelationVO> relations

) {}
