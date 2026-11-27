package com.edu.ai.learning.controller;

import com.edu.ai.common.BusinessException;
import com.edu.ai.common.Result;
import com.edu.ai.learning.dto.StudentLearningReportDetailVO;
import com.edu.ai.learning.dto.StudentLearningReportRequest;
import com.edu.ai.learning.dto.StudentLearningReportVO;
import com.edu.ai.learning.service.StudentLearningReportService;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * F6-03：学生阶段学习报告接口。
 */
@RestController
@RequestMapping("/api/v1/student/courses/{courseId}/learning-reports")
@PreAuthorize("hasRole('STUDENT')")
public class StudentLearningReportController {

    private final StudentLearningReportService service;

    public StudentLearningReportController(
            StudentLearningReportService service) {
        this.service = service;
    }

    /**
     * 生成一份新的阶段学习报告。
     */
    @PostMapping
    public Result<StudentLearningReportDetailVO> generate(
            @PathVariable Long courseId,
            @Valid @RequestBody StudentLearningReportRequest request,
            Authentication authentication) {

        return Result.success(
                service.generate(
                        courseId,
                        currentUserId(authentication),
                        request
                )
        );
    }

    /**
     * 查询当前学生在该课程中的报告历史。
     */
    @GetMapping
    public Result<List<StudentLearningReportVO>> list(
            @PathVariable Long courseId,
            Authentication authentication) {

        return Result.success(
                service.listForStudent(
                        courseId,
                        currentUserId(authentication)
                )
        );
    }

    /**
     * 查询当前学生的一份报告详情。
     */
    @GetMapping("/{reportId}")
    public Result<StudentLearningReportDetailVO> detail(
            @PathVariable Long courseId,
            @PathVariable Long reportId,
            Authentication authentication) {

        return Result.success(
                service.getForStudent(
                        courseId,
                        reportId,
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
