package com.edu.ai.admin.controller;

import com.edu.ai.admin.dto.CoursePageVO;
import com.edu.ai.admin.service.AdminCourseService;
import com.edu.ai.audit.entity.AdminOperationLog;
import com.edu.ai.common.BusinessException;
import com.edu.ai.common.Result;
import com.edu.ai.course.entity.Course;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminCourseController {

    private final AdminCourseService courseService;

    public AdminCourseController(
            AdminCourseService courseService) {

        this.courseService = courseService;
    }

    // 查询全部课程
    @GetMapping("/courses")
    public Result<CoursePageVO> listCourses(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Long ownerId) {

        return Result.success(
                courseService.listCourses(
                        current,
                        size,
                        name,
                        ownerId
                )
        );
    }

    // 查询课程详情
    @GetMapping("/courses/{courseId}")
    public Result<Course> detail(
            @PathVariable Long courseId) {

        return Result.success(
                courseService.getCourse(courseId)
        );
    }

    // 停用课程
    @PutMapping("/courses/{courseId}/disable")
    public Result<String> disable(
            @PathVariable Long courseId,
            Authentication authentication) {

        courseService.disableCourse(
                courseId,
                getAdminId(authentication)
        );

        return Result.success("课程已停用");
    }

    // 恢复课程
    @PutMapping("/courses/{courseId}/enable")
    public Result<String> enable(
            @PathVariable Long courseId,
            Authentication authentication) {

        courseService.enableCourse(
                courseId,
                getAdminId(authentication)
        );

        return Result.success("课程已恢复");
    }

    // 查询管理员操作日志
    @GetMapping("/operation-logs")
    public Result<List<AdminOperationLog>> logs(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) Long adminId) {

        return Result.success(
                courseService.listLogs(
                        current,
                        size,
                        adminId
                )
        );
    }

    private Long getAdminId(
            Authentication authentication) {

        if (authentication == null
                || !(authentication.getPrincipal()
                        instanceof Long adminId)) {

            throw new BusinessException(
                    401, "登录状态无效");
        }

        return adminId;
    }
}
