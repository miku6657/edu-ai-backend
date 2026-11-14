package com.edu.ai.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.edu.ai.system.entity.UserConfig;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserConfigMapper
        extends BaseMapper<UserConfig> {
}
