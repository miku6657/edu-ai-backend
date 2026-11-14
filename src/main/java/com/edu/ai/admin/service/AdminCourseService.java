package com.edu.ai.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import com.edu.ai.admin.dto.CoursePageVO;
import com.edu.ai.audit.entity.AdminOperationLog;
import com.edu.ai.audit.mapper.AdminOperationLogMapper;
import com.edu.ai.common.BusinessException;
import com.edu.ai.course.entity.Course;
import com.edu.ai.course.mapper.CourseMapper;
import com.edu.ai.system.entity.User;
import com.edu.ai.system.mapper.UserMapper;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AdminCourseService {

    private final CourseMapper courseMapper;
    private final UserMapper userMapper;
    private final AdminOperationLogMapper auditMapper;

    public AdminCourseService(
            CourseMapper courseMapper,
            UserMapper userMapper,
            AdminOperationLogMapper auditMapper) {

        this.courseMapper = courseMapper;
        this.userMapper = userMapper;
        this.auditMapper = auditMapper;
    }

    // 管理员分页查询全部课程
    public CoursePageVO listCourses(
            long current,
            long size,
            String name,
            Long ownerId) {

        if (current < 1 || size < 1 || size > 100) {
            throw new BusinessException(
                    400, "分页参数不合法");
        }

        LambdaQueryWrapper<Course> query =
                new LambdaQueryWrapper<Course>()
                        .orderByDesc(Course::getCreateTime)
                        .orderByDesc(Course::getId);

        if (name != null && !name.isBlank()) {

            if (name.length() > 100) {
                throw new BusinessException(
                        400, "查询条件过长");
            }

            query.like(
                    Course::getName,
                    name.trim()
            );
        }

        if (ownerId != null) {
            query.eq(Course::getUserId, ownerId);
        }

        Page<Course> page = courseMapper.selectPage(
                new Page<>(current, size),
                query
        );

        CoursePageVO result = new CoursePageVO();

        result.setCurrent(page.getCurrent());
        result.setSize(page.getSize());
        result.setTotal(page.getTotal());
        result.setPages(page.getPages());
        result.setRecords(page.getRecords());

        return result;
    }

    // 管理员查询课程详情
    public Course getCourse(Long courseId) {
        return requireCourse(courseId);
    }

    // 管理员停用课程
    @Transactional(rollbackFor = Exception.class)
    public void disableCourse(
            Long courseId,
            Long adminId) {

        changeCourseStatus(
                courseId,
                adminId,
                0,
                "DISABLE_COURSE"
        );
    }

    // 管理员恢复课程
    @Transactional(rollbackFor = Exception.class)
    public void enableCourse(
            Long courseId,
            Long adminId) {

        changeCourseStatus(
                courseId,
                adminId,
                1,
                "ENABLE_COURSE"
        );
    }

    private void changeCourseStatus(
            Long courseId,
            Long adminId,
            int targetStatus,
            String operation) {

        Course course = requireCourse(courseId);
        requireAdmin(adminId);

        if (Integer.valueOf(targetStatus)
                .equals(course.getStatus())) {

            throw new BusinessException(
                    409, "课程已经是当前状态");
        }

        // 以旧状态作为更新条件，防止并发覆盖
        int updated = courseMapper.update(
                null,
                new LambdaUpdateWrapper<Course>()
                        .eq(Course::getId, courseId)
                        .eq(
                                Course::getStatus,
                                course.getStatus()
                        )
                        .set(
                                Course::getStatus,
                                targetStatus
                        )
                        .set(
                                Course::getUpdateTime,
                                LocalDateTime.now()
                        )
        );

        if (updated != 1) {
            throw new BusinessException(
                    409, "课程状态已变化，请重试");
        }

        // 操作日志和课程状态在同一个数据库事务内
        AdminOperationLog log = new AdminOperationLog();

        log.setAdminId(adminId);
        log.setOperation(operation);
        log.setTargetType("COURSE");
        log.setTargetId(courseId);

        String action = targetStatus == 1
                ? "恢复"
                : "停用";

        log.setDescription(
                action + "课程：" + course.getName()
        );

        log.setCreateTime(LocalDateTime.now());

        auditMapper.insert(log);
    }

    // 查询最近的操作日志
    public List<AdminOperationLog> listLogs(
            long current,
            long size,
            Long adminId) {

        if (current < 1 || size < 1 || size > 100) {
            throw new BusinessException(
                    400, "分页参数不合法");
        }

        LambdaQueryWrapper<AdminOperationLog> query =
                new LambdaQueryWrapper<AdminOperationLog>()
                        .orderByDesc(
                                AdminOperationLog::getCreateTime
                        )
                        .orderByDesc(
                                AdminOperationLog::getId
                        );

        if (adminId != null) {
            query.eq(
                    AdminOperationLog::getAdminId,
                    adminId
            );
        }

        return auditMapper.selectPage(
                new Page<>(current, size),
                query
        ).getRecords();
    }

    private Course requireCourse(Long courseId) {

        if (courseId == null || courseId <= 0) {
            throw new BusinessException(
                    400, "课程ID不合法");
        }

        Course course = courseMapper.selectById(courseId);

        if (course == null) {
            throw new BusinessException(
                    404, "课程不存在");
        }

        return course;
    }

    private void requireAdmin(Long adminId) {

        User user = userMapper.selectById(adminId);

        if (user == null
                || !Integer.valueOf(1)
                        .equals(user.getStatus())
                || !"ADMIN".equals(user.getRole())) {

            throw new BusinessException(
                    403, "管理员权限已失效");
        }
    }
}
