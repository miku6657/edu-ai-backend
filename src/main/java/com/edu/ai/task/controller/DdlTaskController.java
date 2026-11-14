package com.edu.ai.task.controller;

import com.edu.ai.common.BusinessException;
import com.edu.ai.common.Result;

import com.edu.ai.task.dto.DdlTaskCreateRequest;
import com.edu.ai.task.dto.DdlTaskUpdateRequest;
import com.edu.ai.task.dto.DdlTaskVO;
import com.edu.ai.task.dto.DraftDecisionRequest;
import com.edu.ai.task.service.DdlTaskService;
import com.edu.ai.task.service.TaskChangeDraftService;

import jakarta.validation.Valid;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ddl/tasks")
public class DdlTaskController {

    private final DdlTaskService ddlTaskService;
    private final TaskChangeDraftService draftService;

    public DdlTaskController(
            DdlTaskService ddlTaskService,
            TaskChangeDraftService draftService) {
        this.ddlTaskService = ddlTaskService;
        this.draftService = draftService;
    }

    // 创建个人任务
    @PostMapping
    public Result<DdlTaskVO> create(
            @Valid @RequestBody DdlTaskCreateRequest request,
            Authentication authentication) {

        return Result.success(
                ddlTaskService.create(
                        getUserId(authentication),
                        request
                )
        );
    }

    // 查询当前用户的任务列表
    @GetMapping
    public Result<List<DdlTaskVO>> list(
            Authentication authentication) {

        return Result.success(
                ddlTaskService.list(
                        getUserId(authentication)
                )
        );
    }

    // 查询任务详情
    @GetMapping("/{taskId}")
    public Result<DdlTaskVO> detail(
            @PathVariable Long taskId,
            Authentication authentication) {

        return Result.success(
                ddlTaskService.detail(
                        taskId,
                        getUserId(authentication)
                )
        );
    }

    // 修改个人任务
    @PutMapping("/{taskId}")
    public Result<DdlTaskVO> update(
            @PathVariable Long taskId,
            @Valid @RequestBody DdlTaskUpdateRequest request,
            Authentication authentication) {

        return Result.success(
                ddlTaskService.update(
                        taskId,
                        getUserId(authentication),
                        request
                )
        );
    }

    // 完成任务
    @PostMapping("/{taskId}/complete")
    public Result<String> complete(
            @PathVariable Long taskId,
            Authentication authentication) {

        ddlTaskService.complete(
                taskId,
                getUserId(authentication)
        );

        return Result.success("任务已完成");
    }

    // 取消任务
    @PostMapping("/{taskId}/cancel")
    public Result<String> cancel(
            @PathVariable Long taskId,
            Authentication authentication) {

        ddlTaskService.cancel(
                taskId,
                getUserId(authentication)
        );

        return Result.success("任务已取消");
    }

    // 撤销完成
    @PostMapping("/{taskId}/reopen")
    public Result<String> reopen(
            @PathVariable Long taskId,
            Authentication authentication) {

        ddlTaskService.reopen(
                taskId,
                getUserId(authentication)
        );

        return Result.success("已恢复任务");
    }

    // 用户处理任务变更草案
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

    // 从 JWT 认证信息中获取用户 ID
    private Long getUserId(Authentication authentication) {

        if (authentication == null
                || !(authentication.getPrincipal()
                instanceof Long userId)) {

            throw new BusinessException(
                    401, "登录状态无效");
        }

        return userId;
    }
}
