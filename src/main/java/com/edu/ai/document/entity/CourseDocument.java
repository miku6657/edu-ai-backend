package com.edu.ai.document.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
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

    /**
     * 教学资源类型。
     */
    private String resourceType;

    /**
     * 资源来源。
     */
    private String sourceInfo;

    /**
     * 资料有效截止日期。
     * NULL 表示长期有效。
     */
    private LocalDate validUntil;

    /**
     * 资料更新责任人。
     * NULL 表示由课程负责人承担更新责任。
     */
    private Long updateResponsibleId;

    /**
     * 到期前提醒天数。
     */
    private Integer expiryRemindDays;

    /**
     * 同一资源的所有版本共享resourceId。
     */
    private Long resourceId;

    /**
     * 资源版本号。
     */
    private Integer versionNo;

    /**
     * PENDING：待生效
     * CURRENT：当前生效
     * HISTORY：历史版本
     */
    private String versionStatus;

    /**
     * DRAFT：待提交
     * PENDING：待审核
     * APPROVED：审核通过
     * REJECTED：审核驳回
     */
    private String reviewStatus;

    /**
     * 最近一次提交审核的用户。
     */
    private Long reviewSubmitterId;

    /**
     * 最近一次提交审核的时间。
     */
    private LocalDateTime reviewSubmitTime;

    /**
     * 最近一次审核的用户。
     */
    private Long reviewerId;

    /**
     * 最近一次审核时间。
     */
    private LocalDateTime reviewTime;

    /**
     * 最近一次审核意见。
     */
    private String reviewComment;

    /**
     * 知识库质量状态。
     *
     * NORMAL：正常，可以参与检索。
     * SUSPENDED：质量异常，暂停检索。
     */
    private String qualityStatus;

    /**
     * 暂停检索原因。
     */
    private String qualityBlockReason;

    /**
     * 执行暂停操作的用户。
     */
    private Long qualityBlockedBy;

    /**
     * 暂停检索时间。
     */
    private LocalDateTime qualityBlockedAt;

    /**
     * 切片算法版本。
     */
    private Integer chunkingVersion;

    /**
     * 文档解析及索引状态。
     */
    private String status;

    private String errorMessage;

    private String processingToken;

    private LocalDateTime processingStartedAt;

    private Integer attemptCount;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
