package com.edu.ai.task.controller;

import com.edu.ai.common.BusinessException;
import com.edu.ai.common.Result;
import com.edu.ai.task.service.LearningPathAdjustService;
import com.edu.ai.task.service.LearningPathAdjustService.AdjustmentPreview;
import com.edu.ai.task.service.LearningPathWeakReviewService;
import com.edu.ai.task.service.LearningPathWeakReviewApplyService;

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
    private final LearningPathWeakReviewApplyService weakReviewApplyService;

    public LearningPathAdjustController(
            LearningPathAdjustService service,
            LearningPathWeakReviewService weakReviewService,
            LearningPathWeakReviewApplyService weakReviewApplyService) {

        this.service = service;
        this.weakReviewService = weakReviewService;
        this.weakReviewApplyService = weakReviewApplyService;
    }

    public record ApplyRequest(
            @NotBlank String fingerprint
    ) {}

    private Long userId(Authentication auth) {

        if (auth == null
                || !(auth.getPrincipal() instanceof Long id)) {
            throw new BusinessException(
                    401,
                    "登录状态无效"
            );
        }

        return id;
    }

    /**
     * 原有功能：预览普通学习路径调整
     */
    @GetMapping("/{pathId}/adjustment-preview")
    public Result<AdjustmentPreview> preview(
            @PathVariable Long pathId,
            Authentication auth) {

        return Result.success(
                service.preview(
                        pathId,
                        userId(auth)
                )
        );
    }

    /**
     * 原有功能：确认普通学习路径调整
     */
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
     * 查看薄弱点需要追加的复习时间
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

    /**
     * 预览薄弱点复习任务的实际排期。
     * 不修改数据库。
     */
    @GetMapping("/{pathId}/weak-review-plan")
    public Result<LearningPathWeakReviewApplyService.Plan>
    weakReviewPlan(
            @PathVariable Long pathId,
            Authentication auth) {

        return Result.success(
                weakReviewApplyService.preview(
                        pathId,
                        userId(auth)
                )
        );
    }

    /**
     * 学生确认后：
     * 1. 校验预览指纹
     * 2. 调整已有任务
     * 3. 新增复习阶段
     * 4. 同步个人DDL
     */
    @PostMapping("/{pathId}/apply-weak-review")
    public Result<LearningPathWeakReviewApplyService.Plan>
    applyWeakReview(
            @PathVariable Long pathId,
            @Valid @RequestBody ApplyRequest request,
            Authentication auth) {

        return Result.success(
                weakReviewApplyService.apply(
                        pathId,
                        userId(auth),
                        request.fingerprint()
                )
        );
    }
}
