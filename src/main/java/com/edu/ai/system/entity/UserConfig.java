package com.edu.ai.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@TableName("sys_user_config")
public class UserConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String chatProvider;
    private String chatBaseUrl;
    private String chatApiKeyCiphertext;
    private String chatApiKeyFingerprint;
    private String chatModel;

    private String embeddingProvider;
    private String embeddingBaseUrl;
    private String embeddingApiKeyCiphertext;
    private String embeddingApiKeyFingerprint;
    private String embeddingModel;
    private Integer embeddingDim;

    private String timezone;
    private LocalTime dndStartTime;
    private LocalTime dndEndTime;

    private String boundImPlatform;
    private String boundImUid;

    private String qqbotAppId;
    private String qqbotSecretCiphertext;
    private String qqbotSecretFingerprint;
    private String qqbotStatus;
    private String qqbotLastError;
    private Boolean qqbotBound;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
