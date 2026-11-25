package com.edu.ai.document.controller;

import com.edu.ai.common.BusinessException;
import com.edu.ai.common.Result;
import com.edu.ai.document.service.DocumentClassificationService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/document-classification")
public class DocumentClassificationController {

    private final DocumentClassificationService service;

    public DocumentClassificationController(
            DocumentClassificationService service) {
        this.service = service;
    }

    private Long userId(Authentication auth) {
        if (auth == null
                || !(auth.getPrincipal() instanceof Long id)) {
            throw new BusinessException(
                    401, "登录状态无效"
            );
        }
        return id;
    }

    public record CreateChapterRequest(
            @NotBlank String name,
            Integer sortOrder
    ) {}

    public record BatchAssignRequest(
            @NotEmpty List<Long> documentIds,
            List<Long> chapterIds,
            List<Long> pointIds
    ) {}

    /**
     * 教师创建章节。
     */
    @PostMapping("/courses/{courseId}/chapters")
    public Result<String> createChapter(
            @PathVariable Long courseId,
            @Valid @RequestBody CreateChapterRequest request,
            Authentication auth) {

        service.addChapter(
                courseId,
                userId(auth),
                request.name(),
                request.sortOrder()
        );

        return Result.success("章节创建成功");
    }

    /**
     * 修改章节名称和排序。
     */
    @PutMapping("/courses/{courseId}/chapters/{chapterId}")
    public Result<String> updateChapter(
            @PathVariable Long courseId,
            @PathVariable Long chapterId,
            @Valid @RequestBody CreateChapterRequest request,
            Authentication auth) {

        service.updateChapter(
                courseId,
                chapterId,
                userId(auth),
                request.name(),
                request.sortOrder()
        );

        return Result.success("章节修改成功");
    }

    /**
     * 删除课程章节。
     */
    @DeleteMapping("/courses/{courseId}/chapters/{chapterId}")
    public Result<String> deleteChapter(
            @PathVariable Long courseId,
            @PathVariable Long chapterId,
            Authentication auth) {

        service.deleteChapter(
                courseId,
                chapterId,
                userId(auth)
        );

        return Result.success("章节删除成功");
    }

    /**
     * 课程成员查看章节。
     */
    @GetMapping("/courses/{courseId}/chapters")
    public Result<List<DocumentClassificationService.Chapter>>
    chapters(
            @PathVariable Long courseId,
            Authentication auth) {

        return Result.success(
                service.listChapters(
                        courseId,
                        userId(auth)
                )
        );
    }

    /**
     * 查询文档的章节与知识点关联。
     */
    @GetMapping(
            "/courses/{courseId}/documents/{documentId}/tags"
    )
    public Result<DocumentClassificationService.DocumentTags>
    documentTags(
            @PathVariable Long courseId,
            @PathVariable Long documentId,
            Authentication auth) {

        return Result.success(
                service.getDocumentTags(
                        courseId,
                        documentId,
                        userId(auth)
                )
        );
    }

    /**
     * 教师批量挂接/替换章节和知识点。
     */
    @PutMapping("/courses/{courseId}/documents/tags")
    public Result<DocumentClassificationService.BatchResult>
    batchAssign(
            @PathVariable Long courseId,
            @Valid @RequestBody BatchAssignRequest request,
            Authentication auth) {

        return Result.success(
                service.batchAssign(
                        courseId,
                        userId(auth),
                        request.documentIds(),
                        request.chapterIds() == null
                                ? List.of()
                                : request.chapterIds(),
                        request.pointIds() == null
                                ? List.of()
                                : request.pointIds()
                )
        );
    }

    /**
     * 批量查询当前课程文档的分类信息。
     */
    @GetMapping("/courses/{courseId}/documents/tags")
    public Result<List<
            DocumentClassificationService.DocumentClassification>>
    documentClassifications(
            @PathVariable Long courseId,
            Authentication auth) {

        return Result.success(
                service.listDocumentClassifications(
                        courseId,
                        userId(auth)
                )
        );
    }
}
