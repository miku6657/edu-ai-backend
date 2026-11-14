
package com.edu.ai.course.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.edu.ai.common.BusinessException;
import com.edu.ai.course.dto.CourseDTO;
import com.edu.ai.course.entity.Course;
import com.edu.ai.course.mapper.CourseMapper;
import com.edu.ai.course.service.CourseService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class CourseServiceImpl implements CourseService {

    private final CourseMapper courseMapper;

    public CourseServiceImpl(CourseMapper courseMapper) {
        this.courseMapper = courseMapper;
    }

    // 创建课程
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Course createCourse(CourseDTO dto, Long userId) {

        validateCourseDTO(dto);

        Course course = new Course();

        course.setName(dto.getName().trim());
        course.setDescription(dto.getDescription());
        course.setOwnerId(userId);
        course.setStatus(1);
        course.setCreateTime(LocalDateTime.now());
        course.setUpdateTime(LocalDateTime.now());

        courseMapper.insert(course);

        return course;
    }

    // 修改自己创建的课程
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateCourse(
            Long courseId,
            CourseDTO dto,
            Long userId) {

        Course course = requireMyCourse(courseId, userId);

        validateCourseDTO(dto);

        course.setName(dto.getName().trim());
        course.setDescription(dto.getDescription());
        course.setUpdateTime(LocalDateTime.now());

        courseMapper.updateById(course);
    }

    // 停用自己创建的课程
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void disableCourse(Long courseId, Long userId) {

        Course course = requireMyCourse(courseId, userId);

        course.setStatus(0);
        course.setUpdateTime(LocalDateTime.now());

        courseMapper.updateById(course);
    }

    // 查询当前用户创建的课程
    @Override
    public List<Course> getMyCourses(Long userId) {

        return courseMapper.selectList(
                new LambdaQueryWrapper<Course>()
                        .eq(Course::getOwnerId, userId)
                        .orderByDesc(Course::getCreateTime)
        );
    }

    // 查看课程详情
    @Override
    public Course getCourseDetail(
            Long courseId,
            Long userId) {

        return requireMyCourse(courseId, userId);
    }

    // 检查课程是否属于当前用户
    private Course requireMyCourse(
            Long courseId,
            Long userId) {

        Course course = courseMapper.selectById(courseId);

        if (course == null) {
            throw new BusinessException(404, "课程不存在");
        }

        if (!userId.equals(course.getOwnerId())) {
            throw new BusinessException(
                    403,
                    "无权管理该课程"
            );
        }

        return course;
    }

    // 校验课程参数
    private void validateCourseDTO(CourseDTO dto) {

        if (dto == null
                || dto.getName() == null
                || dto.getName().isBlank()) {

            throw new BusinessException(
                    400,
                    "课程名称不能为空"
            );
        }

        if (dto.getName().trim().length() > 100) {
            throw new BusinessException(
                    400,
                    "课程名称不能超过100个字符"
            );
        }
    }
}
