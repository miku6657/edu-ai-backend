package com.edu.ai.document.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;

import com.edu.ai.document.entity.CourseEmbeddingMigration;
import com.edu.ai.document.mapper.CourseEmbeddingMigrationMapper;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class EmbeddingMigrationLeaseService {

    private static final int LEASE_MINUTES = 35;

    private final CourseEmbeddingMigrationMapper mapper;

    public EmbeddingMigrationLeaseService(
            CourseEmbeddingMigrationMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 初始化任务租约。
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean initialize(
            Long migrationId,
            String token) {

        LocalDateTime now = LocalDateTime.now();

        int updated = mapper.update(
                null,
                new LambdaUpdateWrapper<CourseEmbeddingMigration>()
                        .eq(CourseEmbeddingMigration::getId, migrationId)
                        .eq(CourseEmbeddingMigration::getStatus, "RUNNING")
                        .eq(CourseEmbeddingMigration::getProcessingToken, token)
                        .set(
                                CourseEmbeddingMigration::getLeaseUntil,
                                now.plusMinutes(LEASE_MINUTES)
                        )
                        .set(
                                CourseEmbeddingMigration::getUpdateTime,
                                now
                        )
        );

        return updated == 1;
    }

    /**
     * 续租。
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean renew(
            Long migrationId,
            String token) {

        LocalDateTime now = LocalDateTime.now();

        int updated = mapper.update(
                null,
                new LambdaUpdateWrapper<CourseEmbeddingMigration>()
                        .eq(CourseEmbeddingMigration::getId, migrationId)
                        .eq(CourseEmbeddingMigration::getStatus, "RUNNING")
                        .eq(CourseEmbeddingMigration::getProcessingToken, token)
                        .gt(CourseEmbeddingMigration::getLeaseUntil, now)
                        .set(
                                CourseEmbeddingMigration::getLeaseUntil,
                                now.plusMinutes(LEASE_MINUTES)
                        )
                        .set(
                                CourseEmbeddingMigration::getUpdateTime,
                                now
                        )
        );

        return updated == 1;
    }

    /**
     * 恢复过期任务。
     */
    @Transactional(rollbackFor = Exception.class)
    public int recoverExpired() {

        LocalDateTime now = LocalDateTime.now();

        return mapper.recoverExpiredLeases(
                now,
                now.plusMinutes(1)
        );
    }

    /**
     * 释放租约。
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean release(
            Long migrationId,
            String token) {

        int updated = mapper.update(
                null,
                new LambdaUpdateWrapper<CourseEmbeddingMigration>()
                        .eq(CourseEmbeddingMigration::getId, migrationId)
                        .eq(CourseEmbeddingMigration::getStatus, "RUNNING")
                        .eq(CourseEmbeddingMigration::getProcessingToken, token)
                        .set(CourseEmbeddingMigration::getLeaseUntil, null)
                        .set(CourseEmbeddingMigration::getProcessingToken, null)
                        .set(
                                CourseEmbeddingMigration::getUpdateTime,
                                LocalDateTime.now()
                        )
        );

        return updated == 1;
    }
}
