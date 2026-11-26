package com.edu.ai.document.controller;

import com.edu.ai.common.BusinessException;
import com.edu.ai.common.Result;

import com.edu.ai.document.dto.DocumentReviewDecisionRequest;
import com.edu.ai.document.dto.DocumentReviewDetailVO;
import com.edu.ai.document.dto.DocumentReviewSubmitRequest;

import com.edu.ai.document.service.DocumentReviewService;

import org.springframework.security.core.Authentication;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(
        "/api/v1/document-reviews"
        + "/courses/{courseId}"
        + "/documents/{documentId}"
)
public class DocumentReviewController {

    private final DocumentReviewService service;

    public DocumentReviewController(
            DocumentReviewService service) {

        this.service = service;
    }

    /**
     * 获取当前登录用户ID。
     */
    private Long userId(
            Authentication authentication) {

        if (authentication == null
                || !(authentication.getPrincipal()
                        instanceof Long id)) {

            throw new BusinessException(
                    401,
                    "登录状态无效"
            );
        }

        return id;
    }

    /**
     * 查询文档审核详情。
     */
    @GetMapping
    public Result<DocumentReviewDetailVO> getDetail(

            @PathVariable Long courseId,

            @PathVariable Long documentId,

            Authentication authentication) {

        return Result.success(

                service.getDetail(

                        courseId,

                        documentId,

                        userId(authentication)
                )
        );
    }

    /**
     * 提交知识库发布审核。
     */
    @PostMapping("/submit")
    public Result<DocumentReviewDetailVO> submit(

            @PathVariable Long courseId,

            @PathVariable Long documentId,

            @RequestBody(required = false)
            DocumentReviewSubmitRequest request,

            Authentication authentication) {

        return Result.success(

                service.submit(

                        courseId,

                        documentId,

                        userId(authentication),

                        request == null
                                ? null
                                : request.comment()
                )
        );
    }

    /**
     * 审核通过或者审核驳回。
     */
    @PostMapping("/decision")
    public Result<DocumentReviewDetailVO> decide(

            @PathVariable Long courseId,

            @PathVariable Long documentId,

            @RequestBody
            DocumentReviewDecisionRequest request,

            Authentication authentication) {

        return Result.success(

                service.decide(

                        courseId,

                        documentId,

                        userId(authentication),

                        request.decision(),

                        request.comment()
                )
        );
    }

    /**
     * 撤回待审核申请。
     */
    @PostMapping("/withdraw")
    public Result<DocumentReviewDetailVO> withdraw(

            @PathVariable Long courseId,

            @PathVariable Long documentId,

            @RequestBody(required = false)
            DocumentReviewSubmitRequest request,

            Authentication authentication) {

        return Result.success(

                service.withdraw(

                        courseId,

                        documentId,

                        userId(authentication),

                        request == null
                                ? null
                                : request.comment()
                )
        );
    }
}
