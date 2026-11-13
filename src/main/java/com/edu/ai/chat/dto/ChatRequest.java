package com.edu.ai.chat.dto;

import lombok.Data;

@Data
public class ChatRequest {

    // 课程ID
    private Long courseId;

    // 已有会话ID，首次提问可以不传
    private Long sessionId;

    // 学生的问题
    private String question;
}
