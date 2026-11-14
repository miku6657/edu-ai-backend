package com.edu.ai.task.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@TableName("ddl_task_change_draft")
public class TaskChangeDraft {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long targetTaskId;

    private Long userId;

    private String newTaskTitle;

    private String newTimePrecision;

    private LocalDate newDueDate;

    private LocalTime newDueTime;

    private LocalDateTime newDueAt;

    private Boolean newTimeInferred;

    private String newTimeAmbiguityReason;

    private String newDeliverableFormat;

    private String newSubmissionChannel;

    private String rawSourceText;

    private String rawTimeText;

    private String userTimezone;

    private LocalDateTime expiresAt;

    private String decision;

    private String decisionRequestId;

    private LocalDateTime decidedAt;

    private Long resultTaskId;

    private String status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
