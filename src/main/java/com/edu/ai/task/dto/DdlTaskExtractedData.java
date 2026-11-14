package com.edu.ai.task.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;

@Data
public class DdlTaskExtractedData {

    private String taskTitle;

    private String courseName;

    private String description;

    @JsonProperty("time_precision")
    private String timePrecision;

    private LocalDate dueDate;

    private LocalTime dueTime;

    private LocalDateTime dueAt;

    @JsonProperty("time_inferred")
    private Boolean timeInferred;

    @JsonProperty("time_ambiguity_reason")
    private String timeAmbiguityReason;

    @JsonProperty("raw_time_text")
    private String rawTimeText;

    @JsonProperty("deliverable_format")
    private String deliverableFormat;

    @JsonProperty("submission_channel")
    private String submissionChannel;

    private Double confidence;

    private String extractionReason;
}
