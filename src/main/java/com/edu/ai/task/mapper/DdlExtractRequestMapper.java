package com.edu.ai.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.edu.ai.task.entity.DdlExtractRequest;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface DdlExtractRequestMapper
        extends BaseMapper<DdlExtractRequest> {

    @Select("""
        SELECT *
        FROM ddl_extract_request
        WHERE user_id = #{userId}
          AND client_request_id = #{clientRequestId}
        LIMIT 1
        """)
    DdlExtractRequest findByRequestId(
            @Param("userId") Long userId,
            @Param("clientRequestId") String clientRequestId
    );

    @Select("""
        SELECT *
        FROM ddl_extract_request
        WHERE user_id = #{userId}
          AND client_request_id = #{clientRequestId}
        LIMIT 1
        FOR UPDATE
        """)
    DdlExtractRequest findByRequestIdForUpdate(
            @Param("userId") Long userId,
            @Param("clientRequestId") String clientRequestId
    );

    /**
     * 原子抢占已经过期的 PROCESSING 请求。
     */
    @Update("""
        UPDATE ddl_extract_request
        SET processing_token = #{newToken},
            lease_until = DATE_ADD(
                CURRENT_TIMESTAMP,
                INTERVAL 15 MINUTE
            ),
            updated_at = CURRENT_TIMESTAMP,
            error_message = NULL
        WHERE id = #{id}
          AND status = 'PROCESSING'
          AND lease_until IS NOT NULL
          AND lease_until <= CURRENT_TIMESTAMP
        """)
    int reclaimExpired(
            @Param("id") Long id,
            @Param("newToken") String newToken
    );

    /**
     * 保存成功结果。
     * 必须持有当前处理令牌。
     */
    @Update("""
        UPDATE ddl_extract_request
        SET task_id = #{taskId},
            extract_outcome = #{outcome},
            response_json = #{responseJson},
            status = 'SUCCEEDED',
            error_message = NULL,
            lease_until = NULL,
            updated_at = CURRENT_TIMESTAMP
        WHERE id = #{id}
          AND status = 'PROCESSING'
          AND processing_token = #{token}
        """)
    int markSucceeded(
            @Param("id") Long id,
            @Param("token") String token,
            @Param("taskId") Long taskId,
            @Param("outcome") String outcome,
            @Param("responseJson") String responseJson
    );

    /**
     * 保存失败状态。
     */
    @Update("""
        UPDATE ddl_extract_request
        SET status = 'FAILED',
            error_message = #{errorMessage},
            lease_until = NULL,
            updated_at = CURRENT_TIMESTAMP
        WHERE id = #{id}
          AND status = 'PROCESSING'
          AND processing_token = #{token}
        """)
    int markFailed(
            @Param("id") Long id,
            @Param("token") String token,
            @Param("errorMessage") String errorMessage
    );
}
