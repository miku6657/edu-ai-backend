package com.edu.ai.document.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("course_document")
public class CourseDocument {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long courseId;
    private Long uploaderId;

    private String originalName;
    private String storageName;
    private Long fileSize;
    private String contentType;

    /**
     * 教学资源类型：
     * LESSON_PLAN / COURSEWARE /
     * EXERCISE / PRACTICAL_GUIDE / OTHER
     */
    private String resourceType;

    /**
     * 资源来源，例如：
     * 教师自编、教材配套、实验室资料等
     */
    private String sourceInfo;

    /**
     * 资源根ID。
     * 同一资源的所有版本使用相同的resourceId。
     */
    private Long resourceId;

    /**
     * 资源版本号，从1开始。
     */
    private Integer versionNo;

    /**
     * PENDING：待生效
     * CURRENT：当前生效
     * HISTORY：历史版本
     */
    private String versionStatus;

    private String status;
    private String errorMessage;

    // 当前索引执行任务的唯一标识
    private String processingToken;

    // 开始处理的时间
    private LocalDateTime processingStartedAt;

    // 索引累计尝试次数
    private Integer attemptCount;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
