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

    // AI拒答原因，仅拒答的assistant消息填写
    private String noEvidenceReason;

    // 拒答回答对应的学生问题ID
    private Long questionMessageId;

    private LocalDateTime createTime;
}
