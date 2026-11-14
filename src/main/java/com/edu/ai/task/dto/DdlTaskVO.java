package com.edu.ai.task.dto;

import com.edu.ai.task.entity.DdlTask;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DdlTaskVO {

    private Long id;

    private String displayCode;

    private Long courseId;

    private String courseName;

    private String taskTitle;

    private String deliverableFormat;

    private String submissionChannel;

    private String timePrecision;

    private String rawTimeText;

    private String dueDate;

    private String dueTime;

    private String dueAt;

    private Boolean timeInferred;

    private String timeAmbiguityReason;

    private String status;

    private Boolean overdue;

    private String sourcePlatform;

    private LocalDateTime completedAt;

    private LocalDateTime createdAt;

    public static DdlTaskVO from(DdlTask task) {

        DdlTaskVO vo = new DdlTaskVO();

        vo.setId(task.getTaskId());
        vo.setDisplayCode(task.getDisplayCode());
        vo.setCourseId(task.getCourseId());
        vo.setCourseName(task.getCourseName());

        vo.setTaskTitle(task.getTaskTitle());
        vo.setDeliverableFormat(task.getDeliverableFormat());
        vo.setSubmissionChannel(task.getSubmissionChannel());

        vo.setTimePrecision(task.getTimePrecision());
        vo.setRawTimeText(task.getRawTimeText());

        vo.setDueDate(
                task.getDueDate() == null
                        ? null : task.getDueDate().toString()
        );

        vo.setDueTime(
                task.getDueTime() == null
                        ? null : task.getDueTime().toString()
        );

        vo.setDueAt(
                task.getDueAt() == null
                        ? null : task.getDueAt().toString()
        );

        vo.setTimeInferred(task.getTimeInferred());
        vo.setTimeAmbiguityReason(task.getTimeAmbiguityReason());

        vo.setStatus(task.getStatus());

        vo.setOverdue(
                "ACTIVE".equals(task.getStatus())
                        && task.getDueAt() != null
                        && task.getDueAt().isBefore(LocalDateTime.now())
        );

        vo.setSourcePlatform(task.getSourcePlatform());
        vo.setCompletedAt(task.getCompletedAt());
        vo.setCreatedAt(task.getCreatedAt());

        return vo;
    }
}
