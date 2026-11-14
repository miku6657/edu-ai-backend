package com.edu.ai.task.controller;

import com.edu.ai.common.BusinessException;
import com.edu.ai.common.Result;

import com.edu.ai.task.dto.DdlExtractRequest;
import com.edu.ai.task.dto.DdlExtractResult;
import com.edu.ai.task.service.DdlTaskExtractService;

import jakarta.validation.Valid;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/ddl")
public class DdlTaskExtractController {

    private final DdlTaskExtractService extractService;

    public DdlTaskExtractController(
            DdlTaskExtractService extractService) {

        this.extractService = extractService;
    }

    /**
     * AI 任务提取。
     */
    @PostMapping("/extract")
    public Result<DdlExtractResult> extract(
            @Valid @RequestBody DdlExtractRequest request,
            Authentication authentication) {

        Long userId = getUserId(authentication);

        DdlExtractResult result = extractService.extract(
                userId,
                request
        );

        return Result.success(result);
    }

    /**
     * 获取当前登录用户 ID。
     */
    private Long getUserId(
            Authentication authentication) {

        if (authentication == null
                || !(authentication.getPrincipal()
                instanceof Long userId)) {

            throw new BusinessException(
                    401,
                    "登录状态无效"
            );
        }

        return userId;
    }
}
