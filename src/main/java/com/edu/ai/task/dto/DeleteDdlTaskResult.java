package com.edu.ai.task.dto;

public record DeleteDdlTaskResult(
        Long deletedTaskId,
        String status
) {
}
