package com.edu.ai.notification.controller;

import com.edu.ai.common.BusinessException;
import com.edu.ai.common.Result;
import com.edu.ai.notification.dto.*;
import com.edu.ai.notification.service.NotificationService;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(
            NotificationService service) {
        this.service = service;
    }

    /**
     * 获取通知列表及未读数量。
     *
     * GET /api/v1/notifications
     */
    @GetMapping
    public Result<NotificationListVO> list(
            @RequestParam(
                    name = "read_status",
                    required = false
            )
            String readStatus,

            @RequestParam(defaultValue = "1")
            int page,

            @RequestParam(defaultValue = "20")
            int size,

            Authentication authentication) {

        return Result.success(
                service.list(
                        userId(authentication),
                        readStatus,
                        page,
                        size
                )
        );
    }

    /**
     * 标记单条、多条或全部通知已读。
     *
     * PATCH /api/v1/notifications/read
     */
    @PatchMapping("/read")
    public Result<MarkReadResultVO> markRead(
            @RequestBody MarkReadRequest request,
            Authentication authentication) {

        return Result.success(
                service.markRead(
                        userId(authentication),
                        request
                )
        );
    }

    /**
     * 从现有JWT认证对象中获取用户ID。
     */
    private Long userId(
            Authentication authentication) {

        if (authentication == null
                || !(authentication.getPrincipal()
                        instanceof Long id)) {

            throw new BusinessException(
                    401, "请先登录"
            );
        }

        return id;
    }
}
