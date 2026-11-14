package com.edu.ai.task.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class DdlTaskBoardVO {

    @JsonProperty("pending_inbox_tasks")
    private List<DdlTaskVO> pendingInboxTasks;

    @JsonProperty("pending_change_drafts")
    private List<TaskChangeDraftVO> pendingChangeDrafts;

    @JsonProperty("active_tasks")
    private List<DdlTaskVO> activeTasks;

    @JsonProperty("completed_tasks")
    private List<DdlTaskVO> completedTasks;
}
