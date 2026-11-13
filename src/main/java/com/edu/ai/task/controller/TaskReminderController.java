package com.edu.ai.task.controller;

import com.edu.ai.common.BusinessException;
import com.edu.ai.common.Result;
import com.edu.ai.task.dto.TaskReminderSummaryVO;
import com.edu.ai.task.service.TaskReminderService;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/task-reminders")
@PreAuthorize("hasRole('STUDENT')")
public class TaskReminderController {

    private final TaskReminderService reminderService;

    public TaskReminderController(
            TaskReminderService reminderService) {

        this.reminderService = reminderService;
    }

    @GetMapping
    public Result<TaskReminderSummaryVO> getReminders(
            Authentication authentication) {

        if (authentication == null
                || !(authentication.getPrincipal()
                        instanceof Long studentId)) {

            throw new BusinessException(
                    401, "登录状态无效"
            );
        }

        return Result.success(
                reminderService.getReminders(studentId)
        );
    }
}
