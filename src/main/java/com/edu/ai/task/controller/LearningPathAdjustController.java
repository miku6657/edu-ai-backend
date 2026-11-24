package com.edu.ai.task.controller;

import com.edu.ai.common.BusinessException;
import com.edu.ai.common.Result;
import com.edu.ai.task.service.LearningPathAdjustService;
import com.edu.ai.task.service.LearningPathAdjustService.AdjustmentPreview;
import com.edu.ai.task.service.LearningPathWeakReviewService;

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
    private final LearningPathWeakReviewService weakReviewService;

    public LearningPathAdjustController(
            LearningPathAdjustService service,
            LearningPathWeakReviewService weakReviewService) {
        this.service = service;
        this.weakReviewService = weakReviewService;
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

    /**
     * 查看高频提问产生的额外复习建议。
     * 不修改任何学习路径任务。
     */
    @GetMapping("/{pathId}/weak-review-preview")
    public Result<LearningPathWeakReviewService.WeakReviewPreview>
    weakReviewPreview(
            @PathVariable Long pathId,
            Authentication auth) {

        return Result.success(
                weakReviewService.preview(
                        pathId,
                        userId(auth)
                )
        );
    }
}
