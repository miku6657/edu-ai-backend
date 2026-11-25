package com.edu.ai.course.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("course")
public class Course {

    @TableId(type = IdType.AUTO)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /**
     * 课程编码，业务唯一标识
     * 与学生加入课程使用的邀请码不同
     */
    @TableField("course_code")
    private String courseCode;

    private String name;

    private String description;

    /**
     * 学分，例如 3.0、3.5
     */
    private BigDecimal credits;

    /**
     * 适用专业
     */
    @TableField("applicable_major")
    private String applicableMajor;

    /**
     * 开课学期
     */
    private String semester;

    /**
     * 课程负责人
     * 数据库字段为 user_id
     */
    @TableField("user_id")
    @JsonProperty("ownerId")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    /**
     * 1：正常
     * 0：停用
     */
    private Integer status;

    /**
     * 当前生效的 Embedding 索引版本
     */
    private Integer embeddingVersion;

    /**
     * 课程邀请码，不向普通课程详情输出
     */
    @TableField("invite_code")
    @JsonIgnore
    private String inviteCode;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
