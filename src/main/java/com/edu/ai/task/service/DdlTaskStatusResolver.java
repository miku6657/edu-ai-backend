package com.edu.ai.task.service;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Objects;

@Component
public class DdlTaskStatusResolver {

    public String resolve(
            String timePrecision,
            LocalDateTime dueAt,
            LocalDateTime now) {

        Objects.requireNonNull(now, "当前时间不能为空");

        // 非法时间、过去时间，需要用户重新确认
        if ("INVALID_OR_PAST".equals(timePrecision)
                || "PENDING_CONFIRM".equals(timePrecision)) {
            return "PENDING_CONFIRM";
        }

        // 原通知没有提供可用截止时间
        if ("VAGUE_OR_MISSING".equals(timePrecision)) {
            return "PENDING_TIME";
        }

        // 没有有效截止时间
        if (dueAt == null) {
            return "PENDING_TIME";
        }

        // 截止时间已经过去
        if (!dueAt.isAfter(now)) {
            return "PENDING_CONFIRM";
        }

        return "ACTIVE";
    }
}
