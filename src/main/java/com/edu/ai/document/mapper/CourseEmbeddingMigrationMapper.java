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

    @Update("""
        UPDATE course_embedding_migration
        SET status = 'PENDING',
            processing_token = NULL,
            lease_until = NULL,
            next_retry_at = #{nextRetryAt},
            error_message = 'Worker租约过期，等待恢复',
            update_time = NOW()
        WHERE status = 'RUNNING'
          AND lease_until IS NOT NULL
          AND lease_until < #{now}
        """)
    int recoverExpiredLeases(
            @Param("now") LocalDateTime now,
            @Param("nextRetryAt") LocalDateTime nextRetryAt
    );
}
