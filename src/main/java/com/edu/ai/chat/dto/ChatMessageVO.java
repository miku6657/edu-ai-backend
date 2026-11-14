package com.edu.ai.chat.dto;

import com.edu.ai.chat.entity.ChatMessage;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 聊天消息响应对象。
 *
 * 消息 ID 和会话 ID 均使用字符串，
 * sourcesJson 保留原始 JSON 字符串。
 */
@Data
public class ChatMessageVO {

    private String id;

    private String sessionId;

    private String role;

    private String content;

    private String sourcesJson;

    private LocalDateTime createTime;

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
