package com.edu.ai.course.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.edu.ai.common.BusinessException;
import com.edu.ai.course.entity.Course;
import com.edu.ai.course.entity.CourseStudent;
import com.edu.ai.course.mapper.CourseMapper;
import com.edu.ai.course.mapper.CourseStudentMapper;
import com.edu.ai.system.entity.User;
import com.edu.ai.system.enums.UserRole;
import com.edu.ai.system.mapper.UserMapper;
import org.springframework.stereotype.Service;

@Service
public class CoursePermissionService {

    private final CourseMapper courseMapper;
    private final CourseStudentMapper studentMapper;
    private final UserMapper userMapper;

    public CoursePermissionService(
            CourseMapper courseMapper,
            CourseStudentMapper studentMapper,
            UserMapper userMapper) {
        this.courseMapper = courseMapper;
        this.studentMapper = studentMapper;
        this.userMapper = userMapper;
    }

    // 查询课程并校验用户有效性
    private User requireActiveUser(Long userId) {
        if (userId == null) {
            throw new BusinessException(401, "请先登录");
        }

        User user = userMapper.selectById(userId);

        if (user == null
                || !Integer.valueOf(1).equals(user.getStatus())) {
            throw new BusinessException(401, "用户不存在或已禁用");
        }

        return user;
    }

    private Course requireCourse(Long courseId) {
        if (courseId == null) {
            throw new BusinessException(400, "课程ID不能为空");
        }

        Course course = courseMapper.selectById(courseId);

        if (course == null) {
            throw new BusinessException(404, "课程不存在");
        }

        return course;
    }

    /**
     * 读取课程权限：
     * ADMIN: 全局
     * TEACHER: 自己创建的课程
     * STUDENT: 已加入的正常课程
     */
    public Course requireRead(Long courseId, Long userId) {
        User user = requireActiveUser(userId);
        Course course = requireCourse(courseId);

        String role = user.getRole();

        if (UserRole.ADMIN.name().equals(role)) {
            return course;
        }

        if (UserRole.TEACHER.name().equals(role)
                && userId.equals(course.getUserId())) {
            return course;
        }

        if (UserRole.STUDENT.name().equals(role)
                && Integer.valueOf(1).equals(course.getStatus())) {

            Long count = studentMapper.selectCount(
                    new LambdaQueryWrapper<CourseStudent>()
                            .eq(CourseStudent::getCourseId, courseId)
                            .eq(CourseStudent::getStudentId, userId)
            );

            if (count != null && count > 0) {
                return course;
            }
        }

        throw new BusinessException(403, "无权访问该课程");
    }

    /**
     * 对正常开放课程的访问权限。
     */
    public Course requireActiveRead(Long courseId, Long userId) {
        Course course = requireRead(courseId, userId);

        if (!Integer.valueOf(1).equals(course.getStatus())) {
            throw new BusinessException(403, "课程已停用");
        }

        return course;
    }

    /**
     * 课程管理权限：
     * 管理员或创建课程的教师。
     */
    public Course requireManage(Long courseId, Long userId) {
        User user = requireActiveUser(userId);
        Course course = requireCourse(courseId);

        if (UserRole.ADMIN.name().equals(user.getRole())) {
            return course;
        }

        if (UserRole.TEACHER.name().equals(user.getRole())
                && userId.equals(course.getUserId())) {
            return course;
        }

        throw new BusinessException(403, "无权管理该课程");
    }

    /**
     * 是否拥有课程管理权限（不抛异常）。
     */
    public boolean canManage(Long courseId, Long userId) {
        User user = requireActiveUser(userId);
        Course course = requireCourse(courseId);

        return UserRole.ADMIN.name().equals(user.getRole())
                || (
                    UserRole.TEACHER.name().equals(user.getRole())
                    && userId.equals(course.getUserId())
                );
    }
}
