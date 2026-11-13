package com.edu.ai.task.dto;

import lombok.Data;
import java.util.List;

@Data
public class TaskReminderSummaryVO {

    private Integer totalPending;

    private Integer dueSoonCount;

    private Integer overdueCount;

    private List<TaskReminderVO> reminders;
}
