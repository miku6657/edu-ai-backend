package com.edu.ai.task.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
public class DdlTaskUpdateRequest {

    @NotBlank(message = "任务标题不能为空")
    @Size(max = 200, message = "任务标题不能超过200个字符")
    private String taskTitle;

    private Long courseId;

    private String courseName;

    private String deliverableFormat;

    private String submissionChannel;

    private String timePrecision;

    private LocalDate dueDate;

    private LocalTime dueTime;

    private LocalDateTime dueAt;

    private Boolean timeInferred;

    private String timeAmbiguityReason;

    private String userTimezone;
}
