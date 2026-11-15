package com.edu.ai.notification.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MarkReadResultVO(
        @JsonProperty("updated_count")
        int updatedCount,

        @JsonProperty("remaining_unread_count")
        long remainingUnreadCount
) {
}
