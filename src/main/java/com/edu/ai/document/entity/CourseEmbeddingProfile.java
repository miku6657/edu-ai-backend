package com.edu.ai.document.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("course_embedding_profile")
public class CourseEmbeddingProfile {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long courseId;
    private Integer embeddingVersion;

    private String provider;
    private String baseUrl;
    private String model;
    private Integer dimension;

    private String collectionName;

    private String apiKeyCiphertext;
    private String apiKeyFingerprint;

    private String status;

    private LocalDateTime createTime;
}
