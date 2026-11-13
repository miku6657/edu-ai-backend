package com.edu.ai.task.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("learning_task")
public class LearningTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long courseId;

    private String title;

    private String description;

    private LocalDateTime deadline;

    // 1：有效，0：已取消
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
