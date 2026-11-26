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

    // AI拒答原因
    private String noEvidenceReason;

    // 对应学生问题ID
    private String questionMessageId;

    // 已创建的工单ID
    private String ticketId;

    // 工单状态
    private String ticketStatus;

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

        vo.setNoEvidenceReason(
                message.getNoEvidenceReason()
        );

        vo.setQuestionMessageId(
                message.getQuestionMessageId() == null
                        ? null
                        : message.getQuestionMessageId().toString()
        );

        return vo;
    }
}
