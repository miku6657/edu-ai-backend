package com.edu.ai.document.dto;

import com.edu.ai.document.entity.CourseEmbeddingMigration;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CourseEmbeddingMigrationVO {

    private String id;

    private String courseId;

    private Integer sourceVersion;

    private Integer targetVersion;

    private String targetModel;

    private String status;

    private Integer totalDocuments;

    private Integer processedDocuments;

    private Integer failedDocuments;

    private Integer progress;

    private String errorMessage;

    private LocalDateTime createTime;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    public static CourseEmbeddingMigrationVO from(
            CourseEmbeddingMigration migration) {

        CourseEmbeddingMigrationVO vo =
                new CourseEmbeddingMigrationVO();

        vo.setId(migration.getId().toString());

        vo.setCourseId(
                migration.getCourseId().toString());

        vo.setSourceVersion(
                migration.getSourceVersion());

        vo.setTargetVersion(
                migration.getTargetVersion());

        vo.setTargetModel(
                migration.getTargetModel());

        vo.setStatus(
                migration.getStatus());

        vo.setTotalDocuments(
                migration.getTotalDocuments());

        vo.setProcessedDocuments(
                migration.getProcessedDocuments());

        vo.setFailedDocuments(
                migration.getFailedDocuments());

        int total = migration.getTotalDocuments() == null
                ? 0
                : migration.getTotalDocuments();

        int processed = migration.getProcessedDocuments() == null
                ? 0
                : migration.getProcessedDocuments();

        vo.setProgress(
                total == 0
                        ? 0
                        : Math.min(100, processed * 100 / total)
        );

        vo.setErrorMessage(
                migration.getErrorMessage());

        vo.setCreateTime(
                migration.getCreateTime());

        vo.setStartedAt(
                migration.getStartedAt());

        vo.setFinishedAt(
                migration.getFinishedAt());

        return vo;
    }
}
