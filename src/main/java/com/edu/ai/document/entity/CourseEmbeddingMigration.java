package com.edu.ai.document.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("course_embedding_migration")
public class CourseEmbeddingMigration {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private Long courseId;

    private Integer sourceVersion;
    private Integer targetVersion;

    private String targetProvider;
    private String targetBaseUrl;
    private String targetModel;
    private Integer targetDimension;

    /**
     * PENDING
     * RUNNING
     * SUCCEEDED
     * FAILED
     * CANCELLED
     */
    private String status;

    private Integer totalDocuments;
    private Integer processedDocuments;
    private Integer failedDocuments;

    private String errorMessage;

    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
