package com.edu.ai.document.controller;

import com.edu.ai.common.BusinessException;
import com.edu.ai.common.Result;

import com.edu.ai.document.dto.ChunkKnowledgeDetailVO;
import com.edu.ai.document.dto.ChunkKnowledgeProgressVO;
import com.edu.ai.document.dto.ChunkKnowledgeRecommendVO;

import com.edu.ai.document.service.ChunkKnowledgeService;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(
        "/api/v1/document-chunks"
        + "/courses/{courseId}"
        + "/documents/{documentId}"
        + "/knowledge"
)
public class ChunkKnowledgeController {

    private final ChunkKnowledgeService service;

    public ChunkKnowledgeController(
            ChunkKnowledgeService service) {

        this.service = service;
    }

    private Long userId(
            Authentication auth) {

        if (auth == null
                || !(auth.getPrincipal()
                        instanceof Long id)) {

            throw new BusinessException(
                    401,
                    "登录状态无效"
            );
        }

        return id;
    }

    public record RecommendRequest(

            List<Long> chunkIds

    ) {}

    public record DecisionRequest(

            List<Long> relationIds,

            String decision

    ) {}

    public record ReplacePointsRequest(

            List<Long> pointIds

    ) {}

    public record ChunkBatchRequest(

            List<Long> chunkIds

    ) {}

    /**
     * 自动生成知识点推荐。
     *
     * 不指定chunkIds时，
     * 自动处理下一批未处理切片。
     */
    @PostMapping("/recommend")
    public Result<ChunkKnowledgeRecommendVO>
            recommend(

            @PathVariable Long courseId,

            @PathVariable Long documentId,

            @RequestBody(required = false)
            RecommendRequest request,

            Authentication auth) {

        return Result.success(
                service.recommend(

                        courseId,

                        documentId,

                        userId(auth),

                        request == null
                                ? null
                                : request.chunkIds()
                )
        );
    }

    /**
     * 查询切片知识点关联详情。
     */
    @GetMapping("/chunks/{chunkId}")
    public Result<ChunkKnowledgeDetailVO>
            getDetail(

            @PathVariable Long courseId,

            @PathVariable Long documentId,

            @PathVariable Long chunkId,

            Authentication auth) {

        return Result.success(
                service.getDetail(

                        courseId,

                        documentId,

                        chunkId,

                        userId(auth)
                )
        );
    }

    /**
     * 批量确认或拒绝知识点推荐。
     */
    @PostMapping("/decisions")
    public Result<
            ChunkKnowledgeService.BatchResult
            > decideRelations(

            @PathVariable Long courseId,

            @PathVariable Long documentId,

            @RequestBody DecisionRequest request,

            Authentication auth) {

        return Result.success(
                service.decideRelations(

                        courseId,

                        documentId,

                        userId(auth),

                        request.relationIds(),

                        request.decision()
                )
        );
    }

    /**
     * 人工设置切片的最终知识点集合。
     */
    @PutMapping("/chunks/{chunkId}/points")
    public Result<
            ChunkKnowledgeService.BatchResult
            > replacePoints(

            @PathVariable Long courseId,

            @PathVariable Long documentId,

            @PathVariable Long chunkId,

            @RequestBody ReplacePointsRequest request,

            Authentication auth) {

        return Result.success(
                service.replaceKnowledgePoints(

                        courseId,

                        documentId,

                        chunkId,

                        userId(auth),

                        request.pointIds()
                )
        );
    }

    /**
     * 批量标记切片无需关联知识点。
     */
    @PostMapping("/not-applicable")
    public Result<
            ChunkKnowledgeService.BatchResult
            > markNotApplicable(

            @PathVariable Long courseId,

            @PathVariable Long documentId,

            @RequestBody ChunkBatchRequest request,

            Authentication auth) {

        return Result.success(
                service.markNotApplicable(

                        courseId,

                        documentId,

                        userId(auth),

                        request.chunkIds()
                )
        );
    }

    /**
     * 批量重置知识点处理状态。
     */
    @PostMapping("/reset")
    public Result<
            ChunkKnowledgeService.BatchResult
            > resetChunks(

            @PathVariable Long courseId,

            @PathVariable Long documentId,

            @RequestBody ChunkBatchRequest request,

            Authentication auth) {

        return Result.success(
                service.resetChunks(

                        courseId,

                        documentId,

                        userId(auth),

                        request.chunkIds()
                )
        );
    }

    /**
     * 查询文档知识点关联进度。
     */
    @GetMapping("/progress")
    public Result<ChunkKnowledgeProgressVO>
            getProgress(

            @PathVariable Long courseId,

            @PathVariable Long documentId,

            Authentication auth) {

        return Result.success(
                service.getProgress(

                        courseId,

                        documentId,

                        userId(auth)
                )
        );
    }
}
