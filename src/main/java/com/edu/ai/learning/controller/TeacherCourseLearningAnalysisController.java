package com.edu.ai.learning.controller;

import com.edu.ai.common.BusinessException;
import com.edu.ai.common.Result;
import com.edu.ai.learning.dto.TeacherCourseLearningAnalysisVO;
import com.edu.ai.learning.service.TeacherCourseLearningAnalysisService;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/teacher/courses/{courseId}/learning-analysis")
@PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
public class TeacherCourseLearningAnalysisController {

    private final TeacherCourseLearningAnalysisService service;

    public TeacherCourseLearningAnalysisController(
            TeacherCourseLearningAnalysisService service) {
        this.service = service;
    }

    @GetMapping
    public Result<TeacherCourseLearningAnalysisVO> overview(
            @PathVariable Long courseId,
            Authentication authentication) {

        return Result.success(
                service.getCourseAnalysis(
                        courseId,
                        currentUserId(authentication)
                )
        );
    }

    private Long currentUserId(Authentication authentication) {

        if (authentication == null
                || !(authentication.getPrincipal() instanceof Long userId)) {

            throw new BusinessException(401, "登录状态无效");
        }

        return userId;
    }
}
