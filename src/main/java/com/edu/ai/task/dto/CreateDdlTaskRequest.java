package com.edu.ai.task.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
public class CreateDdlTaskRequest {

    @JsonProperty("client_request_id")
    private String clientRequestId;

    @JsonProperty("course_id")
    private Long courseId;

    @JsonProperty("course_name")
    private String courseName;

    @JsonProperty("task_title")
    private String taskTitle;

    @JsonProperty("time_precision")
    private String timePrecision;

    @JsonProperty("due_date")
    private LocalDate dueDate;

    @JsonProperty("due_time")
    private LocalTime dueTime;

    @JsonProperty("deliverable_format")
    private String deliverableFormat;

    @JsonProperty("submission_channel")
    private String submissionChannel;
}
