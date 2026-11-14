package com.edu.ai.document.controller;

import com.edu.ai.common.BusinessException;
import com.edu.ai.common.Result;

import com.edu.ai.document.dto.CourseEmbeddingMigrationVO;
import com.edu.ai.document.service.EmbeddingMigrationService;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/embedding-migrations")
public class EmbeddingMigrationController {

    private final EmbeddingMigrationService migrationService;

    public EmbeddingMigrationController(
            EmbeddingMigrationService migrationService) {

        this.migrationService = migrationService;
    }

    /**
     * 创建课程迁移任务。
     */
    @PostMapping
    public Result<CourseEmbeddingMigrationVO> start(
            @RequestParam Long courseId,
            Authentication authentication) {

        Long userId = getUserId(authentication);

        return Result.success(
                migrationService.start(userId, courseId)
        );
    }

    /**
     * 查询单个迁移任务。
     */
    @GetMapping("/{migrationId}")
    public Result<CourseEmbeddingMigrationVO> get(
            @PathVariable Long migrationId,
            Authentication authentication) {

        Long userId = getUserId(authentication);

        return Result.success(
                migrationService.get(userId, migrationId)
        );
    }

    /**
     * 查询当前用户的迁移任务。
     */
    @GetMapping
    public Result<List<CourseEmbeddingMigrationVO>> list(
            @RequestParam(required = false) Long courseId,
            Authentication authentication) {

        Long userId = getUserId(authentication);

        return Result.success(
                migrationService.list(userId, courseId)
        );
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
}
