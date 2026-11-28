package com.edu.ai.audit.controller;

import com.edu.ai.audit.dto.ContentOperationOverviewVO;
import com.edu.ai.audit.service.ContentOperationOverviewService;
import com.edu.ai.common.Result;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/admin/content-operation-overview")
@PreAuthorize("hasRole('ADMIN')")
public class ContentOperationOverviewController {

    private final ContentOperationOverviewService service;

    public ContentOperationOverviewController(
            ContentOperationOverviewService service) {

        this.service = service;
    }

    @GetMapping
    public Result<ContentOperationOverviewVO> overview(

            @RequestParam(required = false)
            Long courseId,

            @RequestParam(required = false)
            @DateTimeFormat(
                    iso = DateTimeFormat.ISO.DATE
            )
            LocalDate startDate,

            @RequestParam(required = false)
            @DateTimeFormat(
                    iso = DateTimeFormat.ISO.DATE
            )
            LocalDate endDate) {

        return Result.success(
                service.getOverview(
                        courseId,
                        startDate,
                        endDate
                )
        );
    }
}
