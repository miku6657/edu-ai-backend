package com.edu.ai.course.controller;

import com.edu.ai.common.BusinessException;
import com.edu.ai.common.Result;
import com.edu.ai.course.dto.CourseDTO;
import com.edu.ai.course.entity.Course;
import com.edu.ai.course.service.CourseService;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/courses")
public class CourseController {

    private final CourseService courseService;

    public CourseController(CourseService courseService) {
        this.courseService = courseService;
    }

    // 负责人创建课程
    @PostMapping
    @PreAuthorize("hasRole('OWNER')")
    public Result<Course> create(
            @RequestBody CourseDTO dto,
            Authentication authentication) {

        Long ownerId = getCurrentUserId(authentication);

        return Result.success(
                courseService.createCourse(dto, ownerId)
        );
    }

    // 负责人修改课程
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('OWNER')")
    public Result<String> update(
            @PathVariable Long id,
            @RequestBody CourseDTO dto,
            Authentication authentication) {

        courseService.updateCourse(
                id, dto, getCurrentUserId(authentication)
        );

        return Result.success("课程修改成功");
    }

    // 负责人停用课程
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('OWNER')")
    public Result<String> disable(
            @PathVariable Long id,
            Authentication authentication) {

        courseService.disableCourse(
                id, getCurrentUserId(authentication)
        );

        return Result.success("课程已停用");
    }

    // 查询我的课程
    @GetMapping("/mine")
    @PreAuthorize("hasAnyRole('OWNER', 'STUDENT')")
    public Result<List<Course>> myCourses(
            Authentication authentication) {

        Long userId = getCurrentUserId(authentication);

        if (hasRole(authentication, "OWNER")) {
            return Result.success(
                    courseService.getOwnerCourses(userId)
            );
        }

        return Result.success(
                courseService.getStudentCourses(userId)
        );
    }

    // 学生查询可加入的课程
    @GetMapping("/available")
    @PreAuthorize("hasRole('STUDENT')")
    public Result<List<Course>> available() {

        return Result.success(
                courseService.getAvailableCourses()
        );
    }

    // 查看课程详情
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER', 'STUDENT')")
    public Result<Course> detail(
            @PathVariable Long id,
            Authentication authentication) {

        Long userId = getCurrentUserId(authentication);

        String role = hasRole(authentication, "OWNER")
                ? "OWNER" : "STUDENT";

        return Result.success(
                courseService.getCourseDetail(id, userId, role)
        );
    }

    // 学生加入课程
    @PostMapping("/{id}/join")
    @PreAuthorize("hasRole('STUDENT')")
    public Result<String> join(
            @PathVariable Long id,
            Authentication authentication) {

        courseService.joinCourse(
                id, getCurrentUserId(authentication)
        );

        return Result.success("加入课程成功");
    }

    // 学生退出课程
    @DeleteMapping("/{id}/join")
    @PreAuthorize("hasRole('STUDENT')")
    public Result<String> leave(
            @PathVariable Long id,
            Authentication authentication) {

        courseService.leaveCourse(
                id, getCurrentUserId(authentication)
        );

        return Result.success("退出课程成功");
    }

    // 从已认证的 JWT 身份中获取用户 ID
    private Long getCurrentUserId(
            Authentication authentication) {

        if (authentication == null
                || !(authentication.getPrincipal() instanceof Long)) {
            throw new BusinessException(401, "登录状态无效");
        }

        return (Long) authentication.getPrincipal();
    }

    // 检查当前用户角色
    private boolean hasRole(
            Authentication authentication, String role) {

        return authentication.getAuthorities().stream()
                .anyMatch(authority ->
                        authority.getAuthority()
                                .equals("ROLE_" + role));
    }
}
