package com.edu.ai.exercise.controller;

import com.edu.ai.common.BusinessException;
import com.edu.ai.common.Result;

import com.edu.ai.exercise.dto.StudentMistakeDetailVO;
import com.edu.ai.exercise.dto.StudentMistakeRetryCreateRequest;
import com.edu.ai.exercise.dto.StudentMistakeRetryListVO;
import com.edu.ai.exercise.dto.StudentMistakeSummaryVO;

import com.edu.ai.exercise.service.StudentMistakeService;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/student/exercises/mistakes")
@PreAuthorize("hasRole('STUDENT')")
public class StudentMistakeController {

    private final StudentMistakeService service;

    public StudentMistakeController(
            StudentMistakeService service) {
        this.service = service;
    }

    /**
     * 查询学生错题。
     *
     * GET /api/v1/student/exercises/mistakes
     * GET /api/v1/student/exercises/mistakes?courseId=1
     */
    @GetMapping
    public Result<StudentMistakeSummaryVO> list(
            @RequestParam(required = false) Long courseId,
            Authentication authentication) {

        return Result.success(
                service.listMistakes(
                        userId(authentication),
                        courseId
                )
        );
    }

    /**
     * 查看错题详情。
     *
     * GET /api/v1/student/exercises/mistakes/{answerId}
     */
    @GetMapping("/{answerId}")
    public Result<StudentMistakeDetailVO> detail(
            @PathVariable Long answerId,
            Authentication authentication) {

        return Result.success(
                service.detail(
                        answerId,
                        userId(authentication)
                )
        );
    }

    /**
     * 生成错题重练清单。
     *
     * POST /api/v1/student/exercises/mistakes/retry-lists
     */
    @PostMapping("/retry-lists")
    public Result<StudentMistakeRetryListVO> createRetryList(
            @Valid
            @RequestBody StudentMistakeRetryCreateRequest request,
            Authentication authentication) {

        return Result.success(
                service.createRetryList(
                        userId(authentication),
                        request
                )
        );
    }

    private Long userId(Authentication authentication) {

        if (authentication == null
                || !(authentication.getPrincipal() instanceof Long id)) {
            throw new BusinessException(401, "登录状态无效");
        }

        return id;
    }
}
