package com.edu.ai.task.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DdlDuplicateCandidateVO {

    @JsonProperty("task_id")
    private Long taskId;

    @JsonProperty("task_title")
    private String taskTitle;

    @JsonProperty("due_at")
    private LocalDateTime dueAt;

    @JsonProperty("similarity")
    private Double similarity;

    @JsonProperty("duplicate_reason")
    private String duplicateReason;
}
