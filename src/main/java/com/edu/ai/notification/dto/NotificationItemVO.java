package com.edu.ai.notification.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record NotificationItemVO(
        @JsonProperty("notification_id")
        String notificationId,

        @JsonProperty("biz_type")
        String bizType,

        @JsonProperty("biz_id")
        String bizId,

        String title,
        String content,

        @JsonProperty("read_status")
        String readStatus,

        @JsonProperty("outbound_status")
        String outboundStatus,

        @JsonProperty("sent_im_at")
        String sentImAt,

        @JsonProperty("read_at")
        String readAt,

        @JsonProperty("created_at")
        String createdAt
) {
}
