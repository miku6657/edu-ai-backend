package com.edu.ai.task.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
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

    private Long userId;

    private String displayCode;

    private Long courseId;

    private String courseName;

    private String taskTitle;

    private String description;

    private String deliverableFormat;

    private String submissionChannel;

    private String rawSourceText;

    private String rawTimeText;

    private String timePrecision;

    // 数据库字段为 is_relative_time
    // Java 属性保持 relativeTime
    @TableField("is_relative_time")
    private Boolean relativeTime;

    private LocalDate dueDate;

    private LocalTime dueTime;

    private LocalDateTime dueAt;

    private Boolean timeInferred;

    private String timeAmbiguityReason;

    private String userTimezone;

    private BigDecimal confidence;

    private String status;

    private LocalDateTime followUpAt;

    private Integer followUpCount;

    private Boolean remindedT24h;

    private Boolean remindedT6h;

    private Boolean remindedOverdue;

    private String sourcePlatform;

    @TableField("learning_path_item_id")
    private Long learningPathItemId;

    private LocalDateTime completedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
