package com.edu.ai.notification.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record MarkReadRequest(
        @JsonProperty("mark_all")
        boolean markAll,

        @JsonProperty("notification_ids")
        List<String> notificationIds
) {
}
