package com.edu.ai.document.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("course_document_review_record")
public class DocumentReviewRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long courseId;

    private Long documentId;

    private Long operatorId;

    /**
     * SUBMIT / APPROVE / REJECT / WITHDRAW
     */
    private String action;

    private String previousStatus;

    private String nextStatus;

    private String comment;

    private LocalDateTime createTime;
}
