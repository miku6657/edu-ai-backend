
package com.edu.ai.course.controller;

import com.edu.ai.common.BusinessException;
import com.edu.ai.common.Result;
import com.edu.ai.course.dto.CourseDTO;
import com.edu.ai.course.entity.Course;
import com.edu.ai.course.service.CourseService;

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

    // 创建课程
    @PostMapping
    public Result<Course> create(
            @RequestBody CourseDTO dto,
            Authentication authentication) {

        return Result.success(
                courseService.createCourse(
                        dto,
                        getCurrentUserId(authentication)
                )
        );
    }

    // 修改课程
    @PutMapping("/{id}")
    public Result<String> update(
            @PathVariable Long id,
            @RequestBody CourseDTO dto,
            Authentication authentication) {

        courseService.updateCourse(
                id,
                dto,
                getCurrentUserId(authentication)
        );

        return Result.success("课程修改成功");
    }

    // 停用课程
    @DeleteMapping("/{id}")
    public Result<String> disable(
            @PathVariable Long id,
            Authentication authentication) {

        courseService.disableCourse(
                id,
                getCurrentUserId(authentication)
        );

        return Result.success("课程已停用");
    }

    // 查询当前用户创建的课程
    @GetMapping("/mine")
    public Result<List<Course>> myCourses(
            Authentication authentication) {

        return Result.success(
                courseService.getMyCourses(
                        getCurrentUserId(authentication)
                )
        );
    }

    // 查看课程详情
    @GetMapping("/{id}")
    public Result<Course> detail(
            @PathVariable Long id,
            Authentication authentication) {

        return Result.success(
                courseService.getCourseDetail(
                        id,
                        getCurrentUserId(authentication)
                )
        );
    }

    // 获取当前登录用户 ID
    private Long getCurrentUserId(
            Authentication authentication) {

        if (authentication == null
                || !(authentication.getPrincipal() instanceof Long userId)) {

            throw new BusinessException(
                    401,
                    "登录状态无效"
            );
        }

        return userId;
    }
}
