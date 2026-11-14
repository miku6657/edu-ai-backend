package com.edu.ai.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.edu.ai.task.entity.DdlExtractRequest;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;

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
}
