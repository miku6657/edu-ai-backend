package com.edu.ai.task.dto;

import com.edu.ai.task.entity.TaskChangeDraft;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
public class TaskChangeDraftVO {

    @JsonProperty("target_task_id")
    private Long targetTaskId;

    @JsonProperty("new_task_title")
    private String newTaskTitle;

    @JsonProperty("new_time_precision")
    private String newTimePrecision;

    @JsonProperty("new_due_date")
    private LocalDate newDueDate;

    @JsonProperty("new_due_time")
    private LocalTime newDueTime;

    @JsonProperty("new_due_at")
    private LocalDateTime newDueAt;

    @JsonProperty("new_time_inferred")
    private Boolean newTimeInferred;

    @JsonProperty("new_time_ambiguity_reason")
    private String newTimeAmbiguityReason;

    @JsonProperty("new_deliverable_format")
    private String newDeliverableFormat;

    @JsonProperty("new_submission_channel")
    private String newSubmissionChannel;

    @JsonProperty("raw_source_text")
    private String rawSourceText;

    @JsonProperty("raw_time_text")
    private String rawTimeText;

    @JsonProperty("user_timezone")
    private String userTimezone;

    @JsonProperty("expires_at")
    private LocalDateTime expiresAt;

    public static TaskChangeDraftVO from(
            TaskChangeDraft draft) {

        TaskChangeDraftVO vo = new TaskChangeDraftVO();

        vo.setTargetTaskId(draft.getTargetTaskId());
        vo.setNewTaskTitle(draft.getNewTaskTitle());
        vo.setNewTimePrecision(
                draft.getNewTimePrecision()
        );

        vo.setNewDueDate(draft.getNewDueDate());
        vo.setNewDueTime(draft.getNewDueTime());
        vo.setNewDueAt(draft.getNewDueAt());

        vo.setNewTimeInferred(
                draft.getNewTimeInferred()
        );

        vo.setNewTimeAmbiguityReason(
                draft.getNewTimeAmbiguityReason()
        );

        vo.setNewDeliverableFormat(
                draft.getNewDeliverableFormat()
        );

        vo.setNewSubmissionChannel(
                draft.getNewSubmissionChannel()
        );

        vo.setRawSourceText(draft.getRawSourceText());
        vo.setRawTimeText(draft.getRawTimeText());

        vo.setUserTimezone(draft.getUserTimezone());
        vo.setExpiresAt(draft.getExpiresAt());

        return vo;
    }
}
