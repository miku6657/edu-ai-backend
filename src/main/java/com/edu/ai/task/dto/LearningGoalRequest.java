package com.edu.ai.task.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record LearningGoalRequest(
        @NotBlank(message = "学习目标不能为空")
        @Size(max = 1000, message = "学习目标最多1000字")
        String goalText,

        @NotNull(message = "每周学习时间不能为空")
        @Min(value = 1, message = "每周至少安排1小时")
        @Max(value = 80, message = "每周学习时间不能超过80小时")
        Integer weeklyHours,

        @NotNull(message = "目标完成日期不能为空")
        @Future(message = "目标日期必须晚于今天")
        LocalDate targetDate
) {}
