package com.edu.ai.document.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("course_embedding_migration")
public class CourseEmbeddingMigration {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private Long courseId;

    private Integer sourceVersion;
    private Integer targetVersion;

    private String targetProvider;
    private String targetBaseUrl;
    private String targetModel;
    private Integer targetDimension;

    /**
     * 迁移任务使用的 API Key 加密快照
     */
    private String targetApiKeyCiphertext;

    /**
     * API Key 的 HMAC 指纹
     */
    private String targetApiKeyFingerprint;

    /**
     * 完整 Embedding 配置指纹
     */
    private String targetConfigFingerprint;

    /**
     * PENDING
     * RUNNING
     * SUCCEEDED
     * FAILED
     * CANCELLED
     */
    private String status;

    private Integer totalDocuments;
    private Integer processedDocuments;
    private Integer failedDocuments;

    private String errorMessage;

    /**
     * 当前 Worker 执行令牌
     */
    private String processingToken;

    /**
     * 迁移执行次数
     */
    private Integer attemptCount;

    /**
     * 下次重试时间
     */
    private LocalDateTime nextRetryAt;

    /**
     * Worker执行租约到期时间
     */
    private LocalDateTime leaseUntil;

    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
