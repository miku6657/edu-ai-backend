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
@PreAuthorize("isAuthenticated()")
public class CourseController {

    private final CourseService courseService;

    public CourseController(CourseService courseService) {
        this.courseService = courseService;
    }

    // 创建课程：所有登录用户
    @PostMapping
    public Result<Course> create(
            @RequestBody CourseDTO dto,
            Authentication authentication) {

        Long userId = getCurrentUserId(authentication);

        return Result.success(
                courseService.createCourse(dto, userId)
        );
    }

    // 修改自己创建的课程
    @PutMapping("/{id}")
    public Result<String> update(
            @PathVariable Long id,
            @RequestBody CourseDTO dto,
            Authentication authentication) {

        courseService.updateCourse(
                id, dto, getCurrentUserId(authentication)
        );

        return Result.success("课程修改成功");
    }

    // 停用自己创建的课程
    @DeleteMapping("/{id}")
    public Result<String> disable(
            @PathVariable Long id,
            Authentication authentication) {

        courseService.disableCourse(
                id, getCurrentUserId(authentication)
        );

        return Result.success("课程已停用");
    }

    // 查询当前用户创建或加入的课程
    @GetMapping("/mine")
    public Result<List<Course>> myCourses(
            Authentication authentication) {

        Long userId = getCurrentUserId(authentication);

        return Result.success(
                courseService.getUserCourses(userId)
        );
    }

    // 查询当前用户可以加入的课程
    @GetMapping("/available")
    public Result<List<Course>> available(
            Authentication authentication) {

        Long userId = getCurrentUserId(authentication);

        return Result.success(
                courseService.getAvailableCourses(userId)
        );
    }

    // 查看课程详情
    @GetMapping("/{id}")
    public Result<Course> detail(
            @PathVariable Long id,
            Authentication authentication) {

        return Result.success(
                courseService.getCourseDetail(
                        id, getCurrentUserId(authentication)
                )
        );
    }

    // 加入课程
    @PostMapping("/{id}/join")
    public Result<String> join(
            @PathVariable Long id,
            Authentication authentication) {

        courseService.joinCourse(
                id, getCurrentUserId(authentication)
        );

        return Result.success("加入课程成功");
    }

    // 退出课程
    @DeleteMapping("/{id}/join")
    public Result<String> leave(
            @PathVariable Long id,
            Authentication authentication) {

        courseService.leaveCourse(
                id, getCurrentUserId(authentication)
        );

        return Result.success("退出课程成功");
    }

    // 从 JWT 认证信息中获取当前用户 ID
    private Long getCurrentUserId(Authentication authentication) {

        if (authentication == null
                || !(authentication.getPrincipal() instanceof Long)) {
            throw new BusinessException(401, "登录状态无效");
        }

        return (Long) authentication.getPrincipal();
    }
}
