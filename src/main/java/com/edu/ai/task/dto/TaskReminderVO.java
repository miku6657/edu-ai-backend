package com.edu.ai.task.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class TaskReminderVO {

    private Long taskId;

    private Long courseId;

    private String courseName;

    private String title;

    private String description;

    private LocalDateTime deadline;

    // NORMAL / DUE_SOON / OVERDUE
    private String reminderType;

    // 距离截止时间的分钟数
    // 逾期后为负数
    private Long remainingMinutes;
}
