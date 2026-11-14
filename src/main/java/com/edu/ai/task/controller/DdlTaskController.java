package com.edu.ai.task.controller;

import com.edu.ai.common.BusinessException;
import com.edu.ai.common.Result;

import com.edu.ai.task.dto.CreateDdlTaskRequest;
import com.edu.ai.task.dto.UpdateDdlTaskRequest;
import com.edu.ai.task.dto.DdlTaskVO;
import com.edu.ai.task.dto.DdlTaskBoardVO;
import com.edu.ai.task.dto.DdlExtractRequest;
import com.edu.ai.task.dto.DdlExtractResult;
import com.edu.ai.task.dto.DraftDecisionRequest;
import com.edu.ai.task.dto.DdlTaskCompletionRequest;
import com.edu.ai.task.dto.DeleteDdlTaskResult;

import com.edu.ai.task.entity.DdlTask;

import com.edu.ai.task.service.DdlTaskService;
import com.edu.ai.task.service.DdlTaskExtractService;
import com.edu.ai.task.service.TaskChangeDraftService;

import jakarta.validation.Valid;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/ddl/tasks")
public class DdlTaskController {

    private final DdlTaskService ddlTaskService;
    private final DdlTaskExtractService extractService;
    private final TaskChangeDraftService draftService;

    public DdlTaskController(
            DdlTaskService ddlTaskService,
            DdlTaskExtractService extractService,
            TaskChangeDraftService draftService) {

        this.ddlTaskService = ddlTaskService;
        this.extractService = extractService;
        this.draftService = draftService;
    }

    /**
     * AI 提取任务
     */
    @PostMapping("/extract")
    public Result<DdlExtractResult> extract(
            @Valid @RequestBody DdlExtractRequest request,
            Authentication authentication) {

        return Result.success(
                extractService.extract(
                        getUserId(authentication),
                        request
                )
        );
    }

    /**
     * 查询个人 DDL 任务看板
     */
    @GetMapping
    public Result<DdlTaskBoardVO> list(
            @RequestParam(
                    name = "course_id",
                    required = false
            ) Long courseId,
            Authentication authentication) {

        return Result.success(
                ddlTaskService.getBoard(
                        getUserId(authentication),
                        courseId
                )
        );
    }

    /**
     * 手动创建任务
     */
    @PostMapping
    public Result<DdlTaskVO> create(
            @Valid @RequestBody CreateDdlTaskRequest request,
            Authentication authentication) {

        DdlTask task = ddlTaskService.createTask(
                getUserId(authentication),
                request
        );

        return Result.success(DdlTaskVO.from(task));
    }

    /**
     * 查询任务详情
     */
    @GetMapping("/{taskId}")
    public Result<DdlTaskVO> detail(
            @PathVariable Long taskId,
            Authentication authentication) {

        DdlTask task = ddlTaskService.requireOwnedTask(
                taskId,
                getUserId(authentication)
        );

        return Result.success(DdlTaskVO.from(task));
    }

    /**
     * 修改任务
     */
    @PutMapping("/{taskId}")
    public Result<DdlTaskVO> update(
            @PathVariable Long taskId,
            @Valid @RequestBody UpdateDdlTaskRequest request,
            Authentication authentication) {

        DdlTask task = ddlTaskService.updateTask(
                taskId,
                getUserId(authentication),
                request
        );

        return Result.success(DdlTaskVO.from(task));
    }

    /**
     * 标记完成或撤销完成
     */
    @PatchMapping("/{taskId}/completion")
    public Result<DdlTaskVO> completion(
            @PathVariable Long taskId,
            @Valid @RequestBody DdlTaskCompletionRequest request,
            Authentication authentication) {

        DdlTask task = ddlTaskService.setCompletion(
                taskId,
                getUserId(authentication),
                request.completed()
        );

        return Result.success(DdlTaskVO.from(task));
    }

    /**
     * 删除任务（逻辑删除）
     */
    @DeleteMapping("/{taskId}")
    public Result<DeleteDdlTaskResult> delete(
            @PathVariable Long taskId,
            Authentication authentication) {

        DdlTask task = ddlTaskService.cancelTask(
                taskId,
                getUserId(authentication)
        );

        return Result.success(
                new DeleteDdlTaskResult(
                        task.getId(),
                        task.getStatus()
                )
        );
    }

    /**
     * 处理 AI 识别出的任务变更草案
     */
    @PostMapping("/{taskId}/draft-decision")
    public Result<DdlTaskVO> decideDraft(
            @PathVariable Long taskId,
            @Valid @RequestBody DraftDecisionRequest request,
            Authentication authentication) {

        DdlTask task = draftService.decide(
                taskId,
                getUserId(authentication),
                request
        );

        return Result.success(DdlTaskVO.from(task));
    }

    /**
     * 获取当前登录用户 ID
     */
    private Long getUserId(Authentication authentication) {

        if (authentication == null
                || !(authentication.getPrincipal()
                instanceof Long userId)) {

            throw new BusinessException(
                    401,
                    "登录状态无效"
            );
        }

        return userId;
    }
}
