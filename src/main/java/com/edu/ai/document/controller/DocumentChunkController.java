package com.edu.ai.document.controller;

import com.edu.ai.common.BusinessException;
import com.edu.ai.common.Result;

import com.edu.ai.document.dto.DocumentChunkVO;
import com.edu.ai.document.dto.DocumentChunkPageVO;

import com.edu.ai.document.service.DocumentChunkService;
import com.edu.ai.document.service.DocumentChunkBackfillService;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(
        "/api/v1/document-chunks"
        + "/courses/{courseId}"
        + "/documents/{documentId}"
)
public class DocumentChunkController {

    private final DocumentChunkService service;

    private final DocumentChunkBackfillService
            backfillService;

    public DocumentChunkController(
            DocumentChunkService service,
            DocumentChunkBackfillService backfillService) {

        this.service = service;
        this.backfillService = backfillService;
    }

    private Long userId(Authentication auth) {

        if (auth == null
                || !(auth.getPrincipal() instanceof Long id)) {

            throw new BusinessException(
                    401,
                    "登录状态无效"
            );
        }

        return id;
    }

    public record AssignChapterRequest(

            List<Long> chunkIds,

            Long chapterId

    ) {}

    public record AssignChapterResult(

            int updatedChunks

    ) {}

    /**
     * 查询文档知识切片列表。
     */
    @GetMapping
    public Result<DocumentChunkPageVO> listChunks(
            @PathVariable Long courseId,

            @PathVariable Long documentId,

            @RequestParam(required = false)
            String keyword,

            @RequestParam(required = false)
            Integer pageNo,

            @RequestParam(required = false)
            Long chapterId,

            @RequestParam(defaultValue = "false")
            Boolean unclassified,

            @RequestParam(defaultValue = "1")
            Integer page,

            @RequestParam(defaultValue = "20")
            Integer pageSize,

            Authentication auth) {

        return Result.success(
                service.listChunks(
                        courseId,
                        documentId,
                        userId(auth),
                        keyword,
                        pageNo,
                        chapterId,
                        unclassified,
                        page,
                        pageSize
                )
        );
    }

    /**
     * 查询切片详情。
     */
    @GetMapping("/{chunkId}")
    public Result<DocumentChunkVO> getChunk(
            @PathVariable Long courseId,

            @PathVariable Long documentId,

            @PathVariable Long chunkId,

            Authentication auth) {

        return Result.success(
                service.getChunk(
                        courseId,
                        documentId,
                        chunkId,
                        userId(auth)
                )
        );
    }

    /**
     * 批量调整切片所属章节。
     */
    @PutMapping("/chapter")
    public Result<AssignChapterResult> assignChapter(
            @PathVariable Long courseId,

            @PathVariable Long documentId,

            @RequestBody AssignChapterRequest request,

            Authentication auth) {

        int count = service.assignChapter(
                courseId,
                documentId,
                userId(auth),
                request.chunkIds(),
                request.chapterId()
        );

        return Result.success(
                new AssignChapterResult(count)
        );
    }

    /**
     * 从当前向量库补录历史资料切片。
     */
    @PostMapping("/backfill")
    public Result<
            DocumentChunkBackfillService.BackfillResult
            > backfill(
            @PathVariable Long courseId,

            @PathVariable Long documentId,

            Authentication auth) {

        return Result.success(
                backfillService.backfill(
                        courseId,
                        documentId,
                        userId(auth)
                )
        );
    }
}
