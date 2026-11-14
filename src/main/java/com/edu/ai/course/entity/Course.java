package com.edu.ai.course.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

import java.time.LocalDateTime;

@Data
@TableName("course")
public class Course {

    @TableId(type = IdType.AUTO)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    // 课程名称
    private String name;

    // 课程描述
    private String description;

    // 所属用户 ID
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    // 课程状态：1 正常，0 停用
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
