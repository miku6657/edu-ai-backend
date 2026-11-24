package com.edu.ai.task.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record LearningGoalVO(
        String id,
        String courseId,
        String studentId,
        String goalText,
        Integer weeklyHours,
        LocalDate targetDate,
        String status,
        LocalDateTime confirmedAt,
        LocalDateTime createTime,
        LocalDateTime updateTime
) {}
