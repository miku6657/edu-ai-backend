package com.edu.ai.document.controller;

import com.edu.ai.common.BusinessException;
import com.edu.ai.common.Result;
import com.edu.ai.document.dto.DocumentSummaryVO;
import com.edu.ai.document.dto.DocumentSummaryUpdateRequest;
import com.edu.ai.document.service.DocumentSummaryService;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(
        "/api/v1/document-summary/courses/{courseId}/documents/{documentId}"
)
@PreAuthorize("hasAnyRole('TEACHER','ADMIN','STUDENT')")
public class DocumentSummaryController {

    private final DocumentSummaryService service;

    public DocumentSummaryController(
            DocumentSummaryService service) {
        this.service = service;
    }

    /**
     * 查询资料摘要。
     */
    @GetMapping
    public Result<DocumentSummaryVO> get(
            @PathVariable Long courseId,
            @PathVariable Long documentId,
            Authentication authentication) {

        return Result.success(
                service.get(
                        courseId,
                        documentId,
                        currentUserId(authentication)
                )
        );
    }

    /**
     * AI生成摘要和章节大纲。
     */
    @PostMapping("/generate")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public Result<DocumentSummaryVO> generate(
            @PathVariable Long courseId,
            @PathVariable Long documentId,
            Authentication authentication) {

        return Result.success(
                service.generate(
                        courseId,
                        documentId,
                        currentUserId(authentication)
                )
        );
    }

    /**
     * 人工编辑待确认摘要。
     */
    @PutMapping("/{summaryId}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public Result<DocumentSummaryVO> updateDraft(
            @PathVariable Long courseId,
            @PathVariable Long documentId,
            @PathVariable Long summaryId,
            @jakarta.validation.Valid
            @RequestBody DocumentSummaryUpdateRequest request,
            Authentication authentication) {

        return Result.success(
                service.updateDraft(
                        courseId,
                        documentId,
                        summaryId,
                        currentUserId(authentication),
                        request.summary(),
                        request.outline()
                )
        );
    }

    /**
     * 课程负责人确认摘要。
     */
    @PostMapping("/{summaryId}/confirm")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public Result<DocumentSummaryVO> confirm(
            @PathVariable Long courseId,
            @PathVariable Long documentId,
            @PathVariable Long summaryId,
            Authentication authentication) {

        return Result.success(
                service.confirm(
                        courseId,
                        documentId,
                        summaryId,
                        currentUserId(authentication)
                )
        );
    }

    private Long currentUserId(
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
