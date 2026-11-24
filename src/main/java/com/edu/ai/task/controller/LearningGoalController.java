package com.edu.ai.task.controller;

import com.edu.ai.common.BusinessException;
import com.edu.ai.common.Result;
import com.edu.ai.task.dto.LearningGoalRequest;
import com.edu.ai.task.dto.LearningGoalVO;
import com.edu.ai.task.service.LearningGoalService;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/learning-goals")
@PreAuthorize("hasRole('STUDENT')")
public class LearningGoalController {

    private final LearningGoalService service;

    public LearningGoalController(LearningGoalService service) {
        this.service = service;
    }

    @GetMapping
    public Result<List<LearningGoalVO>> list(
            Authentication authentication) {

        return Result.success(
                service.listMine(getUserId(authentication))
        );
    }

    @GetMapping("/course/{courseId}")
    public Result<LearningGoalVO> getByCourse(
            @PathVariable Long courseId,
            Authentication authentication) {

        return Result.success(
                service.getByCourse(
                        courseId,
                        getUserId(authentication)
                )
        );
    }

    @PutMapping("/course/{courseId}")
    public Result<LearningGoalVO> saveDraft(
            @PathVariable Long courseId,
            @Valid @RequestBody LearningGoalRequest request,
            Authentication authentication) {

        return Result.success(
                service.saveDraft(
                        courseId,
                        getUserId(authentication),
                        request
                )
        );
    }

    @PostMapping("/{goalId}/confirm")
    public Result<LearningGoalVO> confirm(
            @PathVariable Long goalId,
            Authentication authentication) {

        return Result.success(
                service.confirm(
                        goalId,
                        getUserId(authentication)
                )
        );
    }

    private Long getUserId(Authentication authentication) {

        if (authentication == null
                || !(authentication.getPrincipal()
                instanceof Long userId)) {

            throw new BusinessException(
                    401, "登录状态无效"
            );
        }

        return userId;
    }
}
