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
