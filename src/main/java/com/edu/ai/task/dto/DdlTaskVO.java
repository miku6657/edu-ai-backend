package com.edu.ai.task.dto;

import com.edu.ai.task.entity.DdlTask;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
public class DdlTaskVO {

    @JsonProperty("task_id")
    private Long taskId;

    @JsonProperty("display_code")
    private String displayCode;

    @JsonProperty("course_id")
    private Long courseId;

    @JsonProperty("course_name")
    private String courseName;

    @JsonProperty("task_title")
    private String taskTitle;

    @JsonProperty("deliverable_format")
    private String deliverableFormat;

    @JsonProperty("submission_channel")
    private String submissionChannel;

    @JsonProperty("raw_source_text")
    private String rawSourceText;

    @JsonProperty("raw_time_text")
    private String rawTimeText;

    @JsonProperty("time_precision")
    private String timePrecision;

    @JsonProperty("is_relative_time")
    private Boolean isRelativeTime;

    @JsonProperty("due_date")
    private LocalDate dueDate;

    @JsonProperty("due_time")
    private LocalTime dueTime;

    @JsonProperty("due_at")
    private LocalDateTime dueAt;

    @JsonProperty("time_inferred")
    private Boolean timeInferred;

    @JsonProperty("time_ambiguity_reason")
    private String timeAmbiguityReason;

    @JsonProperty("user_timezone")
    private String userTimezone;

    private BigDecimal confidence;

    private String status;

    @JsonProperty("follow_up_at")
    private LocalDateTime followUpAt;

    @JsonProperty("follow_up_count")
    private Integer followUpCount;

    @JsonProperty("reminded_t24h")
    private Boolean remindedT24h;

    @JsonProperty("reminded_t6h")
    private Boolean remindedT6h;

    @JsonProperty("reminded_overdue")
    private Boolean remindedOverdue;

    @JsonProperty("source_platform")
    private String sourcePlatform;

    @JsonProperty("completed_at")
    private LocalDateTime completedAt;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;

    public static DdlTaskVO from(DdlTask task) {

        DdlTaskVO vo = new DdlTaskVO();

        vo.setTaskId(task.getId());
        vo.setDisplayCode(task.getDisplayCode());
        vo.setCourseId(task.getCourseId());
        vo.setCourseName(task.getCourseName());
        vo.setTaskTitle(task.getTaskTitle());

        vo.setDeliverableFormat(task.getDeliverableFormat());
        vo.setSubmissionChannel(task.getSubmissionChannel());

        vo.setRawSourceText(task.getRawSourceText());
        vo.setRawTimeText(task.getRawTimeText());

        vo.setTimePrecision(task.getTimePrecision());
        vo.setIsRelativeTime(task.getRelativeTime());

        vo.setDueDate(task.getDueDate());
        vo.setDueTime(task.getDueTime());
        vo.setDueAt(task.getDueAt());

        vo.setTimeInferred(task.getTimeInferred());
        vo.setTimeAmbiguityReason(
                task.getTimeAmbiguityReason()
        );

        vo.setUserTimezone(task.getUserTimezone());
        vo.setConfidence(task.getConfidence());

        vo.setStatus(task.getStatus());

        vo.setFollowUpAt(task.getFollowUpAt());
        vo.setFollowUpCount(task.getFollowUpCount());

        vo.setRemindedT24h(task.getRemindedT24h());
        vo.setRemindedT6h(task.getRemindedT6h());
        vo.setRemindedOverdue(task.getRemindedOverdue());

        vo.setSourcePlatform(task.getSourcePlatform());
        vo.setCompletedAt(task.getCompletedAt());

        vo.setCreatedAt(task.getCreatedAt());
        vo.setUpdatedAt(task.getUpdatedAt());

        return vo;
    }
}
