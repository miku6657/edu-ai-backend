package com.edu.ai.chat.dto;

import com.edu.ai.chat.entity.ChatSession;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 聊天会话响应对象。
 *
 * 数据库 Entity 使用 Long，
 * 对外响应统一使用 String，避免 JavaScript BIGINT 精度丢失。
 */
@Data
public class ChatSessionVO {

    private String id;

    private String courseId;

    private String userId;

    private String title;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    public static ChatSessionVO from(ChatSession session) {

        ChatSessionVO vo = new ChatSessionVO();

        vo.setId(session.getId().toString());
        vo.setCourseId(session.getCourseId().toString());
        vo.setUserId(session.getUserId().toString());

        vo.setTitle(session.getTitle());
        vo.setCreateTime(session.getCreateTime());
        vo.setUpdateTime(session.getUpdateTime());

        return vo;
    }
}
