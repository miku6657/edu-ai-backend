package com.edu.ai.course.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.edu.ai.common.BusinessException;
import com.edu.ai.course.dto.CourseDTO;
import com.edu.ai.course.entity.Course;
import com.edu.ai.course.entity.CourseStudent;
import com.edu.ai.course.mapper.CourseMapper;
import com.edu.ai.course.mapper.CourseStudentMapper;
import com.edu.ai.course.service.CourseService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
public class CourseServiceImpl implements CourseService {

    private final CourseMapper courseMapper;
    private final CourseStudentMapper courseStudentMapper;

    public CourseServiceImpl(
            CourseMapper courseMapper,
            CourseStudentMapper courseStudentMapper) {
        this.courseMapper = courseMapper;
        this.courseStudentMapper = courseStudentMapper;
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
            Long courseId, CourseDTO dto, Long userId) {

        Course course = requireCreator(courseId, userId);
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

        Course course = requireCreator(courseId, userId);

        course.setStatus(0);
        course.setUpdateTime(LocalDateTime.now());

        courseMapper.updateById(course);
    }

    /**
     * 查询当前用户的课程：
     * 1. 自己创建的课程（包括已停用课程）
     * 2. 自己加入的正常课程
     */
    @Override
    public List<Course> getUserCourses(Long userId) {

        List<Long> joinedCourseIds = courseStudentMapper.selectList(
                new LambdaQueryWrapper<CourseStudent>()
                        .eq(CourseStudent::getStudentId, userId)
        ).stream()
                .map(CourseStudent::getCourseId)
                .distinct()
                .toList();

        LambdaQueryWrapper<Course> wrapper =
                new LambdaQueryWrapper<>();

        if (joinedCourseIds.isEmpty()) {
            wrapper.eq(Course::getOwnerId, userId);
        } else {
            wrapper.and(query -> query
                    .eq(Course::getOwnerId, userId)
                    .or()
                    .in(Course::getId, joinedCourseIds)
            );
        }

        wrapper.orderByDesc(Course::getCreateTime);

        return courseMapper.selectList(wrapper);
    }

    /**
     * 查询当前用户可以加入的课程：
     * 排除自己创建的课程和已经加入的课程。
     */
    @Override
    public List<Course> getAvailableCourses(Long userId) {

        List<Long> joinedCourseIds = courseStudentMapper.selectList(
                new LambdaQueryWrapper<CourseStudent>()
                        .eq(CourseStudent::getStudentId, userId)
        ).stream()
                .map(CourseStudent::getCourseId)
                .distinct()
                .toList();

        LambdaQueryWrapper<Course> wrapper =
                new LambdaQueryWrapper<Course>()
                        .eq(Course::getStatus, 1)
                        .ne(Course::getOwnerId, userId);

        if (!joinedCourseIds.isEmpty()) {
            wrapper.notIn(Course::getId, joinedCourseIds);
        }

        wrapper.orderByDesc(Course::getCreateTime);

        return courseMapper.selectList(wrapper);
    }

    /**
     * 查看课程详情：
     * 创建者可以查看自己的课程；
     * 其他用户必须先加入课程。
     */
    @Override
    public Course getCourseDetail(Long courseId, Long userId) {

        Course course = requireCourse(courseId);

        // 课程创建者可以查看自己的课程
        if (Objects.equals(course.getOwnerId(), userId)) {
            return course;
        }

        // 其他用户只能查看正常开放的课程
        if (!Integer.valueOf(1).equals(course.getStatus())) {
            throw new BusinessException(403, "课程未开放");
        }

        if (findEnrollment(courseId, userId) == null) {
            throw new BusinessException(403, "请先加入该课程");
        }

        return course;
    }

    // 用户加入课程
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void joinCourse(Long courseId, Long userId) {

        Course course = requireCourse(courseId);

        if (!Integer.valueOf(1).equals(course.getStatus())) {
            throw new BusinessException(400, "该课程暂不可加入");
        }

        // 创建者不需要加入自己的课程
        if (Objects.equals(course.getOwnerId(), userId)) {
            throw new BusinessException(400, "不能加入自己创建的课程");
        }

        if (findEnrollment(courseId, userId) != null) {
            throw new BusinessException(400, "你已经加入该课程");
        }

        CourseStudent enrollment = new CourseStudent();
        enrollment.setCourseId(courseId);
        enrollment.setStudentId(userId);
        enrollment.setJoinTime(LocalDateTime.now());

        courseStudentMapper.insert(enrollment);
    }

    // 用户退出课程
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void leaveCourse(Long courseId, Long userId) {

        Course course = requireCourse(courseId);

        if (Objects.equals(course.getOwnerId(), userId)) {
            throw new BusinessException(400, "课程创建者不能退出自己的课程");
        }

        CourseStudent enrollment =
                findEnrollment(courseId, userId);

        if (enrollment == null) {
            throw new BusinessException(400, "你尚未加入该课程");
        }

        courseStudentMapper.deleteById(enrollment.getId());
    }

    // 查询课程，不存在则抛异常
    private Course requireCourse(Long courseId) {

        Course course = courseMapper.selectById(courseId);

        if (course == null) {
            throw new BusinessException(404, "课程不存在");
        }

        return course;
    }

    // 校验当前用户是否为课程创建者
    private Course requireCreator(Long courseId, Long userId) {

        Course course = requireCourse(courseId);

        if (!Objects.equals(course.getOwnerId(), userId)) {
            throw new BusinessException(403, "只能管理自己创建的课程");
        }

        return course;
    }

    // 查询用户是否已经加入课程
    private CourseStudent findEnrollment(
            Long courseId, Long userId) {

        return courseStudentMapper.selectOne(
                new LambdaQueryWrapper<CourseStudent>()
                        .eq(CourseStudent::getCourseId, courseId)
                        .eq(CourseStudent::getStudentId, userId)
        );
    }

    // 校验课程参数
    private void validateCourseDTO(CourseDTO dto) {

        if (dto == null || dto.getName() == null
                || dto.getName().isBlank()) {
            throw new BusinessException(400, "课程名称不能为空");
        }

        if (dto.getName().trim().length() > 100) {
            throw new BusinessException(400, "课程名称不能超过100个字符");
        }
    }
}
