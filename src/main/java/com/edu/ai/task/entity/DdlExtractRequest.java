package com.edu.ai.task.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ddl_extract_request")
public class DdlExtractRequest {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /**
     * 前端传入的请求唯一标识
     */
    private String clientRequestId;

    /**
     * 请求内容 SHA-256 摘要
     */
    private String requestHash;

    /**
     * 最终创建或命中的任务 ID
     */
    private Long taskId;

    /**
     * CREATED
     * IDEMPOTENT_HIT
     * SEMANTIC_DUPLICATE_HINT
     * CHANGE_DRAFT_CONFIRM_REQUIRED
     */
    private String extractOutcome;

    /**
     * 保存完整响应 JSON。
     * 网络重试时可以直接恢复原响应。
     */
    private String responseJson;

    /**
     * PROCESSING：处理中
     * SUCCEEDED：成功
     * FAILED：失败
     */
    private String status;

    private String errorMessage;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    /**
     * 当前处理线程的唯一令牌。
     * 旧线程不能使用新线程的令牌提交结果。
     */
    private String processingToken;

    /**
     * 当前处理租约的截止时间。
     */
    private LocalDateTime leaseUntil;
}
