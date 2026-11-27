package com.edu.ai.exercise.controller;

import com.edu.ai.common.BusinessException;
import com.edu.ai.common.Result;
import com.edu.ai.exercise.dto.*;
import com.edu.ai.exercise.service.ExerciseService;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/exercises")
public class ExerciseController {

    private final ExerciseService service;

    public ExerciseController(ExerciseService service) {
        this.service = service;
    }

    @GetMapping("/manage")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public Result<List<ExerciseVO>> listManaged(
            @RequestParam(required = false) Long courseId,
            Authentication authentication) {
        return Result.success(service.listManaged(
                userId(authentication), role(authentication), courseId));
    }

    @GetMapping("/courses/{courseId}/knowledge-points")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public Result<List<ExerciseKnowledgePointVO>> knowledgePoints(
            @PathVariable Long courseId,
            Authentication authentication) {
        return Result.success(service.listKnowledgePoints(
                courseId, userId(authentication)));
    }

    @PostMapping("/courses/{courseId}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public Result<ExerciseVO> create(
            @PathVariable Long courseId,
            @Valid @RequestBody ExerciseSaveRequest request,
            Authentication authentication) {
        return Result.success(service.create(
                courseId, userId(authentication), request));
    }

    @PutMapping("/{exerciseId}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public Result<ExerciseVO> update(
            @PathVariable Long exerciseId,
            @Valid @RequestBody ExerciseSaveRequest request,
            Authentication authentication) {
        return Result.success(service.update(
                exerciseId, userId(authentication), request));
    }

    @PostMapping("/{exerciseId}/publish")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public Result<ExerciseVO> publish(
            @PathVariable Long exerciseId,
            Authentication authentication) {
        return Result.success(service.publish(
                exerciseId, userId(authentication)));
    }

    @PostMapping("/{exerciseId}/close")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public Result<ExerciseVO> close(
            @PathVariable Long exerciseId,
            Authentication authentication) {
        return Result.success(service.close(
                exerciseId, userId(authentication)));
    }

    @DeleteMapping("/{exerciseId}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public Result<String> deleteDraft(
            @PathVariable Long exerciseId,
            Authentication authentication) {
        service.deleteDraft(exerciseId, userId(authentication));
        return Result.success("练习草稿已删除");
    }

    @GetMapping("/{exerciseId}")
    public Result<ExerciseVO> detail(
            @PathVariable Long exerciseId,
            Authentication authentication) {
        return Result.success(service.getDetail(
                exerciseId, userId(authentication)));
    }

    private Long userId(Authentication authentication) {
        if (authentication == null
                || !(authentication.getPrincipal() instanceof Long id)) {
            throw new BusinessException(401, "登录状态无效");
        }
        return id;
    }

    private String role(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities() == null) {
            throw new BusinessException(401, "登录状态无效");
        }
        return authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .filter(value -> value.startsWith("ROLE_"))
                .map(value -> value.substring(5))
                .findFirst()
                .orElseThrow(() -> new BusinessException(403, "无法识别当前用户角色"));
    }
}
