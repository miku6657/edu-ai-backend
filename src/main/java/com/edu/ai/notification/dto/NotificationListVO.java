package com.edu.ai.notification.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record NotificationListVO(
        @JsonProperty("unread_count")
        long unreadCount,

        int page,
        int size,
        long total,

        @JsonProperty("total_pages")
        long totalPages,

        List<NotificationItemVO> items
) {
}
