package com.edu.ai.document.controller;

import com.edu.ai.common.BusinessException;
import com.edu.ai.common.Result;
import com.edu.ai.document.dto.DocumentVO;
import com.edu.ai.document.service.DocumentService;
import com.edu.ai.document.service.DocumentIndexTask;
import com.edu.ai.document.service.DocumentLifecycleService;

import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import org.springframework.security.core.Authentication;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService documentService;
    private final DocumentIndexTask indexTask;
    private final DocumentLifecycleService lifecycleService;

    public DocumentController(
            DocumentService documentService,
            DocumentIndexTask indexTask,
            DocumentLifecycleService lifecycleService) {

        this.documentService = documentService;
        this.indexTask = indexTask;
        this.lifecycleService = lifecycleService;
    }

    // 上传课件
    @PostMapping(
            value = "/course/{courseId}",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public Result<DocumentVO> upload(
            @PathVariable Long courseId,

            @RequestParam("file")
            MultipartFile file,

            @RequestParam(
                    value = "resourceType",
                    required = false,
                    defaultValue = "AUTO"
            )
            String resourceType,

            @RequestParam(
                    value = "sourceInfo",
                    required = false
            )
            String sourceInfo,

            Authentication authentication) {

        Long userId = getUserId(authentication);

        DocumentVO document = documentService.upload(
                courseId,
                userId,
                file,
                resourceType,
                sourceInfo
        );

        indexTask.process(document.getId());

        return Result.success(document);
    }

    // 查看课程课件
    @GetMapping("/course/{courseId}")
    public Result<List<DocumentVO>> list(
            @PathVariable Long courseId,
            Authentication authentication) {

        return Result.success(
                documentService.listByCourse(
                        courseId,
                        getUserId(authentication)
                )
        );
    }

    // 下载课件
    @GetMapping("/{documentId}/download")
    public ResponseEntity<Resource> download(
            @PathVariable Long documentId,
            Authentication authentication) {

        DocumentService.DownloadFile file =
                documentService.download(
                        documentId,
                        getUserId(authentication)
                );

        ContentDisposition disposition =
                ContentDisposition.attachment()
                        .filename(
                                file.filename(),
                                StandardCharsets.UTF_8
                        )
                        .build();

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        disposition.toString()
                )
                .body(file.resource());
    }

    // PDF 在线预览
    @GetMapping("/{documentId}/preview")
    public ResponseEntity<Resource> preview(
            @PathVariable Long documentId,
            Authentication authentication) {

        DocumentService.DownloadFile file =
                documentService.download(
                        documentId,
                        getUserId(authentication)
                );

        ContentDisposition disposition =
                ContentDisposition.inline()
                        .filename(
                                file.filename(),
                                StandardCharsets.UTF_8
                        )
                        .build();

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        disposition.toString()
                )
                .header(
                        HttpHeaders.CACHE_CONTROL,
                        "private, no-store"
                )
                .header(
                        "X-Content-Type-Options",
                        "nosniff"
                )
                .body(file.resource());
    }

    // 删除课件
    @DeleteMapping("/{documentId}")
    public Result<String> delete(
            @PathVariable Long documentId,
            Authentication authentication) {

        lifecycleService.delete(
                documentId,
                getUserId(authentication)
        );

        return Result.success("文件删除成功");
    }

    // 重新处理失败的课件
    @PostMapping("/{documentId}/retry")
    public Result<String> retry(
            @PathVariable Long documentId,
            Authentication authentication) {

        lifecycleService.retryIndex(
                documentId,
                getUserId(authentication)
        );

        return Result.success("已提交重新处理任务");
    }

    // 获取当前登录用户 ID
    private Long getUserId(Authentication authentication) {

        if (authentication == null
                || !(authentication.getPrincipal() instanceof Long userId)) {

            throw new BusinessException(
                    401,
                    "登录状态无效"
            );
        }

        return userId;
    }
}
