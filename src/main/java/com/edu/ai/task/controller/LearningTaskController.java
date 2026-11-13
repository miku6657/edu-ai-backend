package com.edu.ai.task.controller;

import com.edu.ai.common.BusinessException;
import com.edu.ai.common.Result;

import com.edu.ai.task.dto.LearningTaskRequest;
import com.edu.ai.task.dto.StudentTaskVO;
import com.edu.ai.task.entity.LearningTask;
import com.edu.ai.task.service.LearningTaskService;

import org.springframework.security.access.prepost.PreAuthorize;
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

    // 负责人创建任务
    @PostMapping("/course/{courseId}")
    @PreAuthorize("hasRole('OWNER')")
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

    // 负责人修改任务
    @PutMapping("/{taskId}")
    @PreAuthorize("hasRole('OWNER')")
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

    // 负责人取消任务
    @DeleteMapping("/{taskId}")
    @PreAuthorize("hasRole('OWNER')")
    public Result<String> cancel(
            @PathVariable Long taskId,
            Authentication authentication) {

        taskService.cancel(
                taskId,
                getUserId(authentication)
        );

        return Result.success("任务已取消");
    }

    // 负责人查看课程任务列表
    @GetMapping("/course/{courseId}/owner")
    @PreAuthorize("hasRole('OWNER')")
    public Result<List<LearningTask>> ownerTasks(
            @PathVariable Long courseId,
            Authentication authentication) {

        return Result.success(
                taskService.listForOwner(
                        courseId,
                        getUserId(authentication)
                )
        );
    }

    // 学生查看自己课程的任务
    @GetMapping("/course/{courseId}/student")
    @PreAuthorize("hasRole('STUDENT')")
    public Result<List<StudentTaskVO>> studentTasks(
            @PathVariable Long courseId,
            Authentication authentication) {

        return Result.success(
                taskService.listForStudent(
                        courseId,
                        getUserId(authentication)
                )
        );
    }

    // 学生标记任务完成
    @PutMapping("/{taskId}/complete")
    @PreAuthorize("hasRole('STUDENT')")
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

    // 学生撤销完成状态
    @PutMapping("/{taskId}/uncomplete")
    @PreAuthorize("hasRole('STUDENT')")
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

    // 负责人查看完成统计
    @GetMapping("/{taskId}/progress")
    @PreAuthorize("hasRole('OWNER')")
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
