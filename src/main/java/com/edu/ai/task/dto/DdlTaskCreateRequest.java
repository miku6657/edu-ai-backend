package com.edu.ai.task.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
public class DdlTaskCreateRequest {

    @NotBlank(message = "任务标题不能为空")
    @Size(max = 200, message = "任务标题不能超过200个字符")
    private String taskTitle;

    private Long courseId;

    private String courseName;

    private String deliverableFormat;

    private String submissionChannel;

    private String rawSourceText;

    private String rawTimeText;

    private String timePrecision;

    private Boolean relativeTime = false;

    private LocalDate dueDate;

    private LocalTime dueTime;

    private LocalDateTime dueAt;

    private Boolean timeInferred = false;

    private String timeAmbiguityReason;

    private String userTimezone = "Asia/Shanghai";

    private String status;

    private String sourcePlatform = "MANUAL";
}
