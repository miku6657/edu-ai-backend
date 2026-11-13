package com.edu.ai.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.edu.ai.task.entity.LearningTask;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface LearningTaskMapper
        extends BaseMapper<LearningTask> {
}
