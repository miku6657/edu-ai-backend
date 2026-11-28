package com.edu.ai.document.controller;

import com.edu.ai.common.BusinessException;
import com.edu.ai.common.Result;
import com.edu.ai.document.dto.DocumentExpiryRequest;
import com.edu.ai.document.dto.DocumentExpiryVO;
import com.edu.ai.document.service.DocumentExpiryService;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(
        "/api/v1/document-expiry/courses/{courseId}"
)
@PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
public class DocumentExpiryController {

    private final DocumentExpiryService service;

    public DocumentExpiryController(
            DocumentExpiryService service) {

        this.service = service;
    }

    /**
     * 查询资料有效期。
     */
    @GetMapping("/documents/{documentId}")
    public Result<DocumentExpiryVO> getExpiry(
            @PathVariable Long courseId,
            @PathVariable Long documentId,
            Authentication authentication) {

        return Result.success(
                service.getExpiry(
                        courseId,
                        documentId,
                        currentUserId(authentication)
                )
        );
    }

    /**
     * 修改资料有效期。
     */
    @PutMapping("/documents/{documentId}")
    public Result<DocumentExpiryVO> updateExpiry(
            @PathVariable Long courseId,
            @PathVariable Long documentId,
            @Valid @RequestBody DocumentExpiryRequest request,
            Authentication authentication) {

        return Result.success(
                service.updateExpiry(
                        courseId,
                        documentId,
                        currentUserId(authentication),
                        request
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
