
package com.edu.ai.course.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("course")
public class Course {

    @TableId(type = IdType.AUTO)
    private Long id;

    // 课程名称
    private String name;

    // 课程描述
    private String description;

    // 课程负责人 ID
    private Long ownerId;

    // 课程状态：1 正常，0 停用
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
