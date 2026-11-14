package com.edu.ai.task.controller;

import com.edu.ai.common.BusinessException;
import com.edu.ai.common.Result;

import com.edu.ai.task.dto.LearningTaskRequest;
import com.edu.ai.task.dto.StudentTaskVO;
import com.edu.ai.task.entity.LearningTask;
import com.edu.ai.task.service.LearningTaskService;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tasks")
public class LearningTaskController {

    private final LearningTaskService taskService;

    public LearningTaskController(
            LearningTaskService taskService) {
        this.taskService = taskService;
    }

    // 创建课程任务
    @PostMapping("/course/{courseId}")
    public Result<LearningTask> create(
            @PathVariable Long courseId,
            @RequestBody LearningTaskRequest request,
            Authentication authentication) {

        return Result.success(
                taskService.create(
                        courseId,
                        getUserId(authentication),
                        request
                )
        );
    }

    // 修改任务
    @PutMapping("/{taskId}")
    public Result<String> update(
            @PathVariable Long taskId,
            @RequestBody LearningTaskRequest request,
            Authentication authentication) {

        taskService.update(
                taskId,
                getUserId(authentication),
                request
        );

        return Result.success("修改成功");
    }

    // 取消任务
    @DeleteMapping("/{taskId}")
    public Result<String> cancel(
            @PathVariable Long taskId,
            Authentication authentication) {

        taskService.cancel(
                taskId,
                getUserId(authentication)
        );

        return Result.success("任务已取消");
    }

    // 查询课程任务及当前用户的完成状态
    @GetMapping("/course/{courseId}")
    public Result<List<StudentTaskVO>> list(
            @PathVariable Long courseId,
            Authentication authentication) {

        return Result.success(
                taskService.listForUser(
                        courseId,
                        getUserId(authentication)
                )
        );
    }

    // 标记任务完成
    @PutMapping("/{taskId}/complete")
    public Result<String> complete(
            @PathVariable Long taskId,
            Authentication authentication) {

        taskService.setCompleted(
                taskId,
                getUserId(authentication),
                true
        );

        return Result.success("任务已完成");
    }

    // 撤销完成状态
    @PutMapping("/{taskId}/uncomplete")
    public Result<String> uncomplete(
            @PathVariable Long taskId,
            Authentication authentication) {

        taskService.setCompleted(
                taskId,
                getUserId(authentication),
                false
        );

        return Result.success("已恢复为未完成");
    }

    // 查看任务完成统计
    // 仍然要求当前用户是课程创建者
    @GetMapping("/{taskId}/progress")
    public Result<Map<String, Object>> progress(
            @PathVariable Long taskId,
            Authentication authentication) {

        return Result.success(
                taskService.progress(
                        taskId,
                        getUserId(authentication)
                )
        );
    }

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
