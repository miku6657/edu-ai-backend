package com.edu.ai.document.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.edu.ai.document.entity.CourseEmbeddingMigration;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface CourseEmbeddingMigrationMapper
        extends BaseMapper<CourseEmbeddingMigration> {

    /**
     * 恢复过期任务。
     *
     * 未达到最大尝试次数：重新进入 PENDING。
     * 达到最大尝试次数：直接 FAILED。
     */
    @Update("""
        UPDATE course_embedding_migration
        SET status = CASE
                WHEN attempt_count >= 3 THEN 'FAILED'
                ELSE 'PENDING'
            END,

            processing_token = NULL,

            lease_until = NULL,

            next_retry_at = CASE
                WHEN attempt_count >= 3 THEN NULL
                ELSE #{nextRetryAt}
            END,

            finished_at = CASE
                WHEN attempt_count >= 3 THEN #{now}
                ELSE finished_at
            END,

            failed_documents = CASE
                WHEN attempt_count >= 3 THEN total_documents
                ELSE failed_documents
            END,

            error_message = 'Worker租约过期，任务已恢复',

            update_time = #{now}

        WHERE status = 'RUNNING'
          AND lease_until IS NOT NULL
          AND lease_until < #{now}
        """)
    int recoverExpiredLeases(
            @Param("now") LocalDateTime now,
            @Param("nextRetryAt") LocalDateTime nextRetryAt
    );
}
