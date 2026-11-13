package com.edu.ai.task.dto;

import com.edu.ai.task.entity.LearningTask;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class StudentTaskVO {

    private Long id;
    private Long courseId;
    private String title;
    private String description;
    private LocalDateTime deadline;

    private Boolean completed;
    private LocalDateTime completedTime;

    private Boolean overdue;

    public static StudentTaskVO of(
            LearningTask task,
            boolean completed,
            LocalDateTime completedTime,
            LocalDateTime now) {

        StudentTaskVO vo = new StudentTaskVO();

        vo.setId(task.getId());
        vo.setCourseId(task.getCourseId());
        vo.setTitle(task.getTitle());
        vo.setDescription(task.getDescription());
        vo.setDeadline(task.getDeadline());
        vo.setCompleted(completed);
        vo.setCompletedTime(completedTime);

        vo.setOverdue(
                !completed &&
                task.getDeadline().isBefore(now)
        );

        return vo;
    }
}
