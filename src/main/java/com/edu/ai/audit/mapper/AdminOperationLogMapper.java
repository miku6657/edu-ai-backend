package com.edu.ai.audit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.edu.ai.audit.entity.AdminOperationLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AdminOperationLogMapper
        extends BaseMapper<AdminOperationLog> {
}
