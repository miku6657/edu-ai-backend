package com.edu.ai.task.controller;

import com.edu.ai.common.BusinessException;
import com.edu.ai.common.Result;
import com.edu.ai.task.dto.TaskReminderSummaryVO;
import com.edu.ai.task.service.TaskReminderService;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/task-reminders")
public class TaskReminderController {

    private final TaskReminderService reminderService;

    public TaskReminderController(
            TaskReminderService reminderService) {
        this.reminderService = reminderService;
    }

    // 查询当前用户的任务提醒
    @GetMapping
    public Result<TaskReminderSummaryVO> getReminders(
            Authentication authentication) {

        Long userId = getUserId(authentication);

        return Result.success(
                reminderService.getReminders(userId)
        );
    }

    // 从 JWT 认证信息获取用户 ID
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
