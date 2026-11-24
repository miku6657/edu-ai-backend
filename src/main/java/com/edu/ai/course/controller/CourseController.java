package com.edu.ai.course.controller;

import com.edu.ai.common.BusinessException;
import com.edu.ai.common.Result;

import com.edu.ai.course.dto.CourseDTO;
import com.edu.ai.course.dto.CourseMemberVO;
import com.edu.ai.course.dto.JoinCourseRequest;

import com.edu.ai.course.entity.Course;

import com.edu.ai.course.service.CourseMembershipService;
import com.edu.ai.course.service.CourseService;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;

import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/courses")
public class CourseController {

    private final CourseService courseService;

    private final CourseMembershipService membershipService;

    public CourseController(
            CourseService courseService,
            CourseMembershipService membershipService) {

        this.courseService = courseService;
        this.membershipService = membershipService;
    }

    /**
     * 教师创建课程
     */
    @PostMapping
    @PreAuthorize("hasRole('TEACHER')")
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

    /**
     * 教师修改课程
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('TEACHER')")
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

    /**
     * 教师停用课程
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('TEACHER')")
    public Result<String> disable(
            @PathVariable Long id,
            Authentication authentication) {

        courseService.disableCourse(
                id,
                getCurrentUserId(authentication)
        );

        return Result.success("课程已停用");
    }

    /**
     * 查询教师创建的课程
     */
    @GetMapping("/mine")
    @PreAuthorize("hasRole('TEACHER')")
    public Result<List<Course>> myCourses(
            Authentication authentication) {

        return Result.success(
                courseService.getMyCourses(
                        getCurrentUserId(authentication)
                )
        );
    }

    /**
     * 查询学生加入的课程
     */
    @GetMapping("/joined")
    @PreAuthorize("hasRole('STUDENT')")
    public Result<List<Course>> joinedCourses(
            Authentication authentication) {

        return Result.success(
                membershipService.getJoinedCourses(
                        getCurrentUserId(authentication)
                )
        );
    }

    /**
     * 学生输入邀请码加入课程
     */
    @PostMapping("/join")
    @PreAuthorize("hasRole('STUDENT')")
    public Result<Course> joinCourse(
            @Valid @RequestBody JoinCourseRequest request,
            Authentication authentication) {

        return Result.success(
                membershipService.joinByInviteCode(
                        getCurrentUserId(authentication),
                        request.getInviteCode()
                )
        );
    }

    /**
     * 查询课程详情
     */
    @GetMapping("/{id}")
    public Result<Course> detail(
            @PathVariable Long id,
            Authentication authentication) {

        return Result.success(
                membershipService.getCourseDetail(
                        id,
                        getCurrentUserId(authentication),
                        getCurrentRole(authentication)
                )
        );
    }

    /**
     * 查看课程成员
     */
    @GetMapping("/{id}/students")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public Result<List<CourseMemberVO>> students(
            @PathVariable Long id,
            Authentication authentication) {

        return Result.success(
                membershipService.getCourseMembers(
                        id,
                        getCurrentUserId(authentication),
                        getCurrentRole(authentication)
                )
        );
    }

    /**
     * 将学生移出课程
     */
    @DeleteMapping("/{id}/students/{studentId}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public Result<String> removeStudent(
            @PathVariable Long id,
            @PathVariable Long studentId,
            Authentication authentication) {

        membershipService.removeStudent(
                id,
                studentId,
                getCurrentUserId(authentication),
                getCurrentRole(authentication)
        );

        return Result.success("学生已移出课程");
    }

    /**
     * 查看课程邀请码
     */
    @GetMapping("/{id}/invite-code")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public Result<Map<String, String>> inviteCode(
            @PathVariable Long id,
            Authentication authentication) {

        String code = membershipService.getInviteCode(
                id,
                getCurrentUserId(authentication),
                getCurrentRole(authentication)
        );

        return Result.success(
                Map.of("inviteCode", code)
        );
    }

    /**
     * 重新生成邀请码
     */
    @PostMapping("/{id}/invite-code")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public Result<Map<String, String>> regenerateInviteCode(
            @PathVariable Long id,
            Authentication authentication) {

        String code = membershipService.regenerateInviteCode(
                id,
                getCurrentUserId(authentication),
                getCurrentRole(authentication)
        );

        return Result.success(
                Map.of("inviteCode", code)
        );
    }

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

    private String getCurrentRole(
            Authentication authentication) {

        if (authentication == null) {
            throw new BusinessException(401, "请先登录");
        }

        return authentication.getAuthorities()
                .stream()
                .map(authority -> authority.getAuthority())
                .filter(authority -> authority.startsWith("ROLE_"))
                .map(authority -> authority.substring(5))
                .findFirst()
                .orElseThrow(() ->
                        new BusinessException(403, "用户角色无效")
                );
    }
}
