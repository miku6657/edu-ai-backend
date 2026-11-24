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

import java.time.LocalDateTime;

@Data
@TableName("course")
public class Course {

    @TableId(type = IdType.AUTO)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String name;

    private String description;

    /**
     * 数据库实际字段 user_id
     * 保留原来的 userId 命名，避免影响其他业务代码
     */
    @TableField("user_id")
    @JsonProperty("ownerId")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    /**
     * 课程状态：1正常，0停用
     */
    private Integer status;

    /**
     * 当前生效的 Embedding 索引版本
     */
    private Integer embeddingVersion;

    /**
     * 课程邀请码
     * 不允许直接通过课程详情返回
     */
    @TableField("invite_code")
    @JsonIgnore
    private String inviteCode;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
