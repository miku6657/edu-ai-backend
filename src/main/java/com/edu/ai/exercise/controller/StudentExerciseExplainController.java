package com.edu.ai.exercise.controller;

import com.edu.ai.common.BusinessException;
import com.edu.ai.common.Result;
import com.edu.ai.exercise.dto.StudentExerciseExplainSessionVO;
import com.edu.ai.exercise.service.StudentExerciseExplainSessionService;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/student/exercises/questions")
@PreAuthorize("hasRole('STUDENT')")
public class StudentExerciseExplainController {

    private final StudentExerciseExplainSessionService service;

    public StudentExerciseExplainController(
            StudentExerciseExplainSessionService service) {
        this.service = service;
    }

    /**
     * 获取或创建某道题的 AI 讲解会话。
     */
    @PostMapping("/{questionId}/explain-session")
    public Result<StudentExerciseExplainSessionVO> getOrCreate(
            @PathVariable Long questionId,
            @RequestParam Long courseId,
            Authentication authentication) {

        return Result.success(
                service.getOrCreate(
                        questionId,
                        courseId,
                        userId(authentication)
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
