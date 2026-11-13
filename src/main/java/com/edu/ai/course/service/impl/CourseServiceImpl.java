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
    public Course createCourse(CourseDTO dto, Long ownerId) {

        validateCourseDTO(dto);

        Course course = new Course();
        course.setName(dto.getName().trim());
        course.setDescription(dto.getDescription());
        course.setOwnerId(ownerId);
        course.setStatus(1);
        course.setCreateTime(LocalDateTime.now());
        course.setUpdateTime(LocalDateTime.now());

        courseMapper.insert(course);

        return course;
    }

    // 修改课程
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateCourse(
            Long courseId, CourseDTO dto, Long ownerId) {

        Course course = requireOwner(courseId, ownerId);
        validateCourseDTO(dto);

        course.setName(dto.getName().trim());
        course.setDescription(dto.getDescription());
        course.setUpdateTime(LocalDateTime.now());

        courseMapper.updateById(course);
    }

    // 停用课程（逻辑停用，不删除课程数据）
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void disableCourse(Long courseId, Long ownerId) {

        Course course = requireOwner(courseId, ownerId);

        course.setStatus(0);
        course.setUpdateTime(LocalDateTime.now());

        courseMapper.updateById(course);
    }

    // 负责人课程列表
    @Override
    public List<Course> getOwnerCourses(Long ownerId) {

        return courseMapper.selectList(
                new LambdaQueryWrapper<Course>()
                        .eq(Course::getOwnerId, ownerId)
                        .orderByDesc(Course::getCreateTime)
        );
    }

    // 学生已加入课程
    @Override
    public List<Course> getStudentCourses(Long studentId) {

        List<Long> courseIds = courseStudentMapper.selectList(
                new LambdaQueryWrapper<CourseStudent>()
                        .eq(CourseStudent::getStudentId, studentId)
        ).stream().map(CourseStudent::getCourseId).toList();

        if (courseIds.isEmpty()) {
            return List.of();
        }

        return courseMapper.selectList(
                new LambdaQueryWrapper<Course>()
                        .in(Course::getId, courseIds)
                        .eq(Course::getStatus, 1)
                        .orderByDesc(Course::getCreateTime)
        );
    }

    // 所有正常开放的课程
    @Override
    public List<Course> getAvailableCourses() {

        return courseMapper.selectList(
                new LambdaQueryWrapper<Course>()
                        .eq(Course::getStatus, 1)
                        .orderByDesc(Course::getCreateTime)
        );
    }

    // 课程详情：负责人或已加入的学生可查看
    @Override
    public Course getCourseDetail(
            Long courseId, Long userId, String role) {

        Course course = requireCourse(courseId);

        if ("OWNER".equals(role)) {
            if (!course.getOwnerId().equals(userId)) {
                throw new BusinessException(403, "无权访问该课程");
            }
            return course;
        }

        if ("STUDENT".equals(role)) {
            if (!Integer.valueOf(1).equals(course.getStatus())) {
                throw new BusinessException(403, "课程未开放");
            }

            if (findEnrollment(courseId, userId) == null) {
                throw new BusinessException(403, "请先加入该课程");
            }

            return course;
        }

        throw new BusinessException(403, "无权访问该课程");
    }

    // 学生加入课程
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void joinCourse(Long courseId, Long studentId) {

        Course course = requireCourse(courseId);

        if (!Integer.valueOf(1).equals(course.getStatus())) {
            throw new BusinessException(400, "该课程暂不可加入");
        }

        if (findEnrollment(courseId, studentId) != null) {
            throw new BusinessException(400, "你已经加入该课程");
        }

        CourseStudent enrollment = new CourseStudent();
        enrollment.setCourseId(courseId);
        enrollment.setStudentId(studentId);
        enrollment.setJoinTime(LocalDateTime.now());

        courseStudentMapper.insert(enrollment);
    }

    // 学生退出课程
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void leaveCourse(Long courseId, Long studentId) {

        CourseStudent enrollment =
                findEnrollment(courseId, studentId);

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

    // 校验课程归属
    private Course requireOwner(Long courseId, Long ownerId) {

        Course course = requireCourse(courseId);

        if (!course.getOwnerId().equals(ownerId)) {
            throw new BusinessException(403, "只能管理自己负责的课程");
        }

        return course;
    }

    // 查询学生是否加入课程
    private CourseStudent findEnrollment(
            Long courseId, Long studentId) {

        return courseStudentMapper.selectOne(
                new LambdaQueryWrapper<CourseStudent>()
                        .eq(CourseStudent::getCourseId, courseId)
                        .eq(CourseStudent::getStudentId, studentId)
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
