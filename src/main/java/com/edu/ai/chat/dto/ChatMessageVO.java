package com.edu.ai.chat.dto;

import com.edu.ai.chat.entity.ChatMessage;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ChatMessageVO {

    private String id;

    private String sessionId;

    private String role;

    private String content;

    private String sourcesJson;

    private LocalDateTime createTime;

    /**
     * 历史引用的版本信息。
     * 动态查询生成，不写入数据库。
     */
    private List<CitationVersion> citationVersions = List.of();

    public record CitationVersion(
            String documentId,
            String documentName,
            String resourceId,
            Integer versionNo,
            String versionStatus,
            String currentDocumentId,
            String currentDocumentName,
            Integer currentVersionNo,
            boolean outdated
    ) {}

    public static ChatMessageVO from(ChatMessage message) {

        ChatMessageVO vo = new ChatMessageVO();

        vo.setId(message.getId().toString());
        vo.setSessionId(message.getSessionId().toString());

        vo.setRole(message.getRole());
        vo.setContent(message.getContent());
        vo.setSourcesJson(message.getSourcesJson());
        vo.setCreateTime(message.getCreateTime());

        return vo;
    }
}
