package com.edu.ai.exercise.controller;

import com.edu.ai.common.BusinessException;
import com.edu.ai.common.Result;
import com.edu.ai.exercise.dto.ExerciseGradeRequest;
import com.edu.ai.exercise.dto.ExerciseReviewDetailVO;
import com.edu.ai.exercise.dto.ExerciseReviewListVO;
import com.edu.ai.exercise.service.ExerciseReviewService;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/exercises/reviews")
@PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
public class ExerciseReviewController {

    private final ExerciseReviewService service;

    public ExerciseReviewController(
            ExerciseReviewService service) {
        this.service = service;
    }

    /**
     * 查询待批改列表。
     *
     * GET /api/v1/exercises/reviews/pending
     * GET /api/v1/exercises/reviews/pending?courseId=1
     */
    @GetMapping("/pending")
    public Result<List<ExerciseReviewListVO>> listPending(
            @RequestParam(required = false) Long courseId,
            Authentication authentication) {

        return Result.success(
                service.listPending(
                        userId(authentication),
                        role(authentication),
                        courseId
                )
        );
    }

    /**
     * 查看某次答题的全部详情。
     *
     * GET /api/v1/exercises/reviews/{attemptId}
     */
    @GetMapping("/{attemptId}")
    public Result<ExerciseReviewDetailVO> detail(
            @PathVariable Long attemptId,
            Authentication authentication) {

        return Result.success(
                service.detail(
                        attemptId,
                        userId(authentication)
                )
        );
    }

    /**
     * 提交批改。
     *
     * POST /api/v1/exercises/reviews/{attemptId}/grade
     */
    @PostMapping("/{attemptId}/grade")
    public Result<ExerciseReviewDetailVO> grade(
            @PathVariable Long attemptId,

            @Valid
            @RequestBody ExerciseGradeRequest request,

            Authentication authentication) {

        return Result.success(
                service.grade(
                        attemptId,
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

    private String role(Authentication authentication) {
        if (authentication == null
                || authentication.getAuthorities() == null) {
            throw new BusinessException(401, "登录状态无效");
        }

        return authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .filter(value -> value.startsWith("ROLE_"))
                .map(value -> value.substring(5))
                .findFirst()
                .orElseThrow(() ->
                        new BusinessException(403, "无法识别当前用户角色"));
    }
}
