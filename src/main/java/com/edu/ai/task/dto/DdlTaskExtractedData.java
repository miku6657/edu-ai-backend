package com.edu.ai.task.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
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
    @JsonAlias("timePrecision")
    private String timePrecision;

    private LocalDate dueDate;

    private LocalTime dueTime;

    private LocalDateTime dueAt;

    @JsonProperty("time_inferred")
    @JsonAlias("timeInferred")
    private Boolean timeInferred;

    // 是否使用相对时间表达
    @JsonProperty("is_relative_time")
    @JsonAlias("isRelativeTime")
    private Boolean relativeTime;

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
