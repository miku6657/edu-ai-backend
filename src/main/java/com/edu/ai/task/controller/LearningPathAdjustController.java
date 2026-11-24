package com.edu.ai.task.controller;

import com.edu.ai.common.BusinessException;
import com.edu.ai.common.Result;
import com.edu.ai.task.service.LearningPathAdjustService;
import com.edu.ai.task.service.LearningPathAdjustService.AdjustmentPreview;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/learning-paths")
@PreAuthorize("hasRole('STUDENT')")
public class LearningPathAdjustController {

    private final LearningPathAdjustService service;

    public LearningPathAdjustController(
            LearningPathAdjustService service) {
        this.service = service;
    }

    public record ApplyRequest(
            @NotBlank String fingerprint
    ) {}

    private Long userId(Authentication auth) {
        if (auth == null
                || !(auth.getPrincipal() instanceof Long id)) {
            throw new BusinessException(
                    401, "登录状态无效"
            );
        }
        return id;
    }

    @GetMapping("/{pathId}/adjustment-preview")
    public Result<AdjustmentPreview> preview(
            @PathVariable Long pathId,
            Authentication auth) {

        return Result.success(
                service.preview(pathId, userId(auth))
        );
    }

    @PostMapping("/{pathId}/adjust")
    public Result<AdjustmentPreview> apply(
            @PathVariable Long pathId,
            @Valid @RequestBody ApplyRequest request,
            Authentication auth) {

        return Result.success(
                service.apply(
                        pathId,
                        userId(auth),
                        request.fingerprint()
                )
        );
    }
}
