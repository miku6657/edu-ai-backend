package com.edu.ai.task.dto;

import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

public record DeleteDdlTaskResult(

        @JsonSerialize(using = ToStringSerializer.class)
        Long deletedTaskId,

        String status

) {
}
