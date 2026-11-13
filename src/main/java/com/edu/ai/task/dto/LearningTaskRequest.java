package com.edu.ai.task.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LearningTaskRequest {

    private String title;

    private String description;

    private LocalDateTime deadline;
}
