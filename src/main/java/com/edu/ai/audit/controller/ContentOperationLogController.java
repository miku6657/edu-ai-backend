package com.edu.ai.audit.controller;

import com.edu.ai.audit.dto.ContentOperationLogPageVO;
import com.edu.ai.audit.service.ContentOperationLogService;
import com.edu.ai.common.Result;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/admin/content-operation-logs")
@PreAuthorize("hasRole('ADMIN')")
public class ContentOperationLogController {

    private final ContentOperationLogService service;

    public ContentOperationLogController(
            ContentOperationLogService service) {

        this.service = service;
    }

    @GetMapping
    public Result<ContentOperationLogPageVO> list(

            @RequestParam(defaultValue = "1")
            long current,

            @RequestParam(defaultValue = "20")
            long size,

            @RequestParam(required = false)
            Long courseId,

            @RequestParam(required = false)
            Long operatorId,

            @RequestParam(required = false)
            String operationType,

            @RequestParam(required = false)
            @DateTimeFormat(
                    iso = DateTimeFormat.ISO.DATE_TIME
            )
            LocalDateTime startTime,

            @RequestParam(required = false)
            @DateTimeFormat(
                    iso = DateTimeFormat.ISO.DATE_TIME
            )
            LocalDateTime endTime) {

        return Result.success(
                service.list(
                        current,
                        size,
                        courseId,
                        operatorId,
                        operationType,
                        startTime,
                        endTime
                )
        );
    }
}
