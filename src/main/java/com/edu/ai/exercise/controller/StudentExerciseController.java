package com.edu.ai.exercise.controller;

import com.edu.ai.common.BusinessException;
import com.edu.ai.common.Result;
import com.edu.ai.exercise.dto.ExerciseAttemptVO;
import com.edu.ai.exercise.dto.ExerciseSubmitRequest;
import com.edu.ai.exercise.dto.ExerciseSubmitResultVO;
import com.edu.ai.exercise.dto.StudentExerciseRecommendationVO;
import com.edu.ai.exercise.dto.StudentExerciseVO;
import com.edu.ai.exercise.service.StudentExerciseRecommendationService;
import com.edu.ai.exercise.service.StudentExerciseService;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/student/exercises")
@PreAuthorize("hasRole('STUDENT')")
public class StudentExerciseController {

    private final StudentExerciseService service;

    private final StudentExerciseRecommendationService recommendationService;

    public StudentExerciseController(
            StudentExerciseService service,
            StudentExerciseRecommendationService recommendationService) {
        this.service = service;
        this.recommendationService = recommendationService;
    }

    /**
     * 查询学生可参加的练习。
     *
     * GET /api/v1/student/exercises
     * GET /api/v1/student/exercises?courseId=1
     */
    @GetMapping
    public Result<List<StudentExerciseVO>> list(
            @RequestParam(required = false) Long courseId,
            Authentication authentication) {

        return Result.success(
                service.listAvailable(
                        userId(authentication),
                        courseId
                )
        );
    }

    /**
     * 获取学生个性化练习推荐。
     *
     * GET /api/v1/student/exercises/recommendations
     * GET /api/v1/student/exercises/recommendations?courseId=1
     */
    @GetMapping("/recommendations")
    public Result<StudentExerciseRecommendationVO> recommendations(
            @RequestParam(required = false) Long courseId,
            Authentication authentication) {

        return Result.success(
                recommendationService.recommend(
                        userId(authentication),
                        courseId
                )
        );
    }

    /**
     * 查看练习详情。
     *
     * GET /api/v1/student/exercises/{exerciseId}
     *
     * 不返回标准答案和答案解析。
     */
    @GetMapping("/{exerciseId}")
    public Result<StudentExerciseVO> detail(
            @PathVariable Long exerciseId,
            Authentication authentication) {

        return Result.success(
                service.detail(
                        exerciseId,
                        userId(authentication)
                )
        );
    }

    /**
     * 开始练习。
     *
     * POST /api/v1/student/exercises/{exerciseId}/attempts
     */
    @PostMapping("/{exerciseId}/attempts")
    public Result<ExerciseAttemptVO> start(
            @PathVariable Long exerciseId,
            Authentication authentication) {

        return Result.success(
                service.startAttempt(
                        exerciseId,
                        userId(authentication)
                )
        );
    }

    /**
     * 提交答案。
     *
     * POST /api/v1/student/exercises/attempts/{attemptId}/submit
     */
    @PostMapping("/attempts/{attemptId}/submit")
    public Result<ExerciseSubmitResultVO> submit(
            @PathVariable Long attemptId,

            @Valid
            @RequestBody ExerciseSubmitRequest request,

            Authentication authentication) {

        return Result.success(
                service.submit(
                        attemptId,
                        userId(authentication),
                        request
                )
        );
    }

    /**
     * 查询某次答题结果。
     *
     * GET /api/v1/student/exercises/attempts/{attemptId}
     */
    @GetMapping("/attempts/{attemptId}")
    public Result<ExerciseSubmitResultVO> result(
            @PathVariable Long attemptId,
            Authentication authentication) {

        return Result.success(
                service.getResult(
                        attemptId,
                        userId(authentication)
                )
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
