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

import org.springframework.security.access.prepost.PreAuthorize;
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

    // 负责人上传 PDF
    @PostMapping(
            value = "/course/{courseId}",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @PreAuthorize("hasRole('OWNER')")
    public Result<DocumentVO> upload(
            @PathVariable Long courseId,
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {

        DocumentVO document = documentService.upload(
                courseId,
                getUserId(authentication),
                file
        );

        indexTask.process(document.getId());

        return Result.success(document);
    }

    // 查看课程资料
    @GetMapping("/course/{courseId}")
    @PreAuthorize("hasAnyRole('OWNER', 'STUDENT')")
    public Result<List<DocumentVO>> list(
            @PathVariable Long courseId,
            Authentication authentication) {

        return Result.success(
                documentService.listByCourse(
                        courseId,
                        getUserId(authentication),
                        getRole(authentication)
                )
        );
    }

    // 下载 PDF
    @GetMapping("/{documentId}/download")
    @PreAuthorize("hasAnyRole('OWNER', 'STUDENT')")
    public ResponseEntity<Resource> download(
            @PathVariable Long documentId,
            Authentication authentication) {

        DocumentService.DownloadFile file =
                documentService.download(
                        documentId,
                        getUserId(authentication),
                        getRole(authentication)
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

    // 负责人删除 PDF
    @DeleteMapping("/{documentId}")
    @PreAuthorize("hasRole('OWNER')")
    public Result<String> delete(
            @PathVariable Long documentId,
            Authentication authentication) {

        lifecycleService.delete(
                documentId,
                getUserId(authentication)
        );

        return Result.success("文件删除成功");
    }

    // 负责人重新处理失败的 PDF
    @PostMapping("/{documentId}/retry")
    @PreAuthorize("hasRole('OWNER')")
    public Result<String> retry(
            @PathVariable Long documentId,
            Authentication authentication) {

        lifecycleService.retryIndex(
                documentId,
                getUserId(authentication)
        );

        return Result.success("已提交重新索引任务");
    }

    private Long getUserId(
            Authentication authentication) {

        if (authentication == null
                || !(authentication.getPrincipal()
                        instanceof Long userId)) {

            throw new BusinessException(
                    401, "登录状态无效");
        }

        return userId;
    }

    private String getRole(
            Authentication authentication) {

        if (authentication.getAuthorities().stream()
                .anyMatch(a ->
                        a.getAuthority()
                                .equals("ROLE_OWNER"))) {
            return "OWNER";
        }

        if (authentication.getAuthorities().stream()
                .anyMatch(a ->
                        a.getAuthority()
                                .equals("ROLE_STUDENT"))) {
            return "STUDENT";
        }

        throw new BusinessException(
                403, "无权访问课程资料"
        );
    }
}
