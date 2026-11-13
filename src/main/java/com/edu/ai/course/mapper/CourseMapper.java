package com.edu.ai.course.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.edu.ai.course.entity.Course;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CourseMapper extends BaseMapper<Course> {
}
