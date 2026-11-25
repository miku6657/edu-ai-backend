package com.edu.ai.document.controller;

import com.edu.ai.common.BusinessException;
import com.edu.ai.common.Result;

import com.edu.ai.document.service.DocumentVersionService;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/documents")
public class DocumentVersionController {

    private final DocumentVersionService service;

    public DocumentVersionController(
            DocumentVersionService service) {

        this.service = service;
    }

    /**
     * 查询指定资源的全部历史版本。
     */
    @GetMapping("/{documentId}/versions")
    public Result<List<DocumentVersionService.VersionInfo>>
    versions(
            @PathVariable Long documentId,
            Authentication authentication) {

        return Result.success(
                service.listVersions(
                        documentId,
                        userId(authentication)
                )
        );
    }

    private Long userId(
            Authentication authentication) {

        if (authentication == null
                || !(authentication.getPrincipal()
                        instanceof Long id)) {

            throw new BusinessException(
                    401, "登录状态无效"
            );
        }

        return id;
    }
}
