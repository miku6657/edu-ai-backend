package com.edu.ai.task.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
public class TaskChangeDraftCreateRequest {

    @NotBlank(message = "任务标题不能为空")
    @Size(max = 200)
    private String newTaskTitle;

    private String newDescription;

    @NotBlank(message = "时间精度不能为空")
    private String newTimePrecision;

    private LocalDate newDueDate;

    private LocalTime newDueTime;

    private LocalDateTime newDueAt;

    private Boolean newTimeInferred;

    private Boolean newRelativeTime;

    private java.math.BigDecimal newConfidence;

    private String newTimeAmbiguityReason;

    private String newDeliverableFormat;

    private String newSubmissionChannel;

    private String rawSourceText;

    private String rawTimeText;

    private String userTimezone;
}
