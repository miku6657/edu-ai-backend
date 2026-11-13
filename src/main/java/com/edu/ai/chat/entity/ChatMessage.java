package com.edu.ai.chat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("chat_message")
public class ChatMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sessionId;

    // user / assistant
    private String role;

    private String content;

    // JSON形式保存引用的PDF来源
    private String sourcesJson;

    private LocalDateTime createTime;
}
