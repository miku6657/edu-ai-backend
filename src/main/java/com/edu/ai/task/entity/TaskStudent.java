package com.edu.ai.task.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("task_student")
public class TaskStudent {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long taskId;

    private Long studentId;

    // 0：未完成，1：已完成
    private Integer completed;

    private LocalDateTime completedTime;
}
