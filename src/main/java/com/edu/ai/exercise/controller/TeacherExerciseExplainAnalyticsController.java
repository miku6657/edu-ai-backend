package com.edu.ai.exercise.controller;

import com.edu.ai.common.BusinessException;
import com.edu.ai.common.Result;
import com.edu.ai.exercise.dto.TeacherExerciseExplainAnalyticsVO;
import com.edu.ai.exercise.service.TeacherExerciseExplainAnalyticsService;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/teacher-exercise-explanations")
@PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
public class TeacherExerciseExplainAnalyticsController {

    private final TeacherExerciseExplainAnalyticsService service;

    public TeacherExerciseExplainAnalyticsController(
            TeacherExerciseExplainAnalyticsService service) {

        this.service = service;
    }

    /**
     * 查询课程 AI 错题讲解统计。
     *
     * GET /api/v1/teacher-exercise-explanations/courses/{courseId}
     */
    @GetMapping("/courses/{courseId}")
    public Result<TeacherExerciseExplainAnalyticsVO> query(
            @PathVariable Long courseId,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate startDate,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate endDate,

            Authentication authentication) {

        if (authentication == null
                || !(authentication.getPrincipal()
                instanceof Long userId)) {

            throw new BusinessException(401, "登录状态无效");
        }

        return Result.success(
                service.query(
                        courseId,
                        userId,
                        startDate,
                        endDate
                )
        );
    }
}
