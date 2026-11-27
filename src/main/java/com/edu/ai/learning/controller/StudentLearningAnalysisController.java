package com.edu.ai.learning.controller;

import com.edu.ai.common.BusinessException;
import com.edu.ai.common.Result;
import com.edu.ai.learning.dto.StudentLearningAnalysisVO;
import com.edu.ai.learning.service.StudentLearningAnalysisService;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/student/learning-analysis")
@PreAuthorize("hasRole('STUDENT')")
public class StudentLearningAnalysisController {

    private final StudentLearningAnalysisService service;

    public StudentLearningAnalysisController(
            StudentLearningAnalysisService service) {
        this.service = service;
    }

    /**
     * 查询当前学生的学习概况和本周学习记录。
     *
     * GET /api/v1/student/learning-analysis
     */
    @GetMapping
    public Result<StudentLearningAnalysisVO> overview(
            Authentication authentication) {

        return Result.success(
                service.getOverview(userId(authentication))
        );
    }

    private Long userId(Authentication authentication) {

        if (authentication == null
                || !(authentication.getPrincipal() instanceof Long id)) {

            throw new BusinessException(
                    401,
                    "登录状态无效"
            );
        }

        return id;
    }
}
