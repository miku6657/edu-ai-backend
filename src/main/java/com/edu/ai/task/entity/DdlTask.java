package com.edu.ai.task.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@TableName("ddl_task")
public class DdlTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    // 所属用户
    private Long userId;

    // 任务显示编号
    private String displayCode;

    // 关联课程，可以为空
    private Long courseId;

    private String courseName;

    // 任务基本信息
    private String taskTitle;

    private String deliverableFormat;

    private String submissionChannel;

    // 原始通知内容
    private String rawSourceText;

    private String rawTimeText;

    // 时间信息
    private String timePrecision;

    private Boolean relativeTime;

    private LocalDate dueDate;

    private LocalTime dueTime;

    private LocalDateTime dueAt;

    private Boolean timeInferred;

    private String timeAmbiguityReason;

    private String userTimezone;

    private BigDecimal confidence;

    // PENDING_TIME、PENDING_CONFIRM、
    // ACTIVE、OVERDUE、COMPLETED、CANCELED
    private String status;

    // 后续跟进
    private LocalDateTime followUpAt;

    private Integer followUpCount;

    // 提醒标记
    private Boolean remindedT24h;

    private Boolean remindedT6h;

    private Boolean remindedOverdue;

    // MANUAL、AI、IMPORT 等
    private String sourcePlatform;

    private LocalDateTime completedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
