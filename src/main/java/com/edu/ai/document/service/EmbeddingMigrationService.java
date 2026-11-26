package com.edu.ai.document.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import com.edu.ai.common.BusinessException;

import com.edu.ai.course.entity.Course;
import com.edu.ai.course.mapper.CourseMapper;

import com.edu.ai.document.dto.CourseEmbeddingMigrationVO;
import com.edu.ai.document.entity.CourseDocument;
import com.edu.ai.document.entity.CourseEmbeddingMigration;
import com.edu.ai.document.mapper.CourseDocumentMapper;
import com.edu.ai.document.mapper.CourseEmbeddingMigrationMapper;

import com.edu.ai.system.entity.UserConfig;
import com.edu.ai.system.mapper.UserConfigMapper;
import com.edu.ai.system.security.ConfigCryptoService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
public class EmbeddingMigrationService {

    private static final Set<String> ACTIVE_STATUSES =
            Set.of("PENDING", "RUNNING");

    private final CourseMapper courseMapper;
    private final CourseDocumentMapper documentMapper;
    private final CourseEmbeddingMigrationMapper migrationMapper;
    private final UserConfigMapper userConfigMapper;
    private final ConfigCryptoService cryptoService;
    private final EmbeddingConfigFingerprint configFingerprint;

    public EmbeddingMigrationService(
            CourseMapper courseMapper,
            CourseDocumentMapper documentMapper,
            CourseEmbeddingMigrationMapper migrationMapper,
            UserConfigMapper userConfigMapper,
            ConfigCryptoService cryptoService,
            EmbeddingConfigFingerprint configFingerprint) {

        this.courseMapper = courseMapper;
        this.documentMapper = documentMapper;
        this.migrationMapper = migrationMapper;
        this.userConfigMapper = userConfigMapper;
        this.cryptoService = cryptoService;
        this.configFingerprint = configFingerprint;
    }

    /**
     * 创建课程 Embedding 迁移任务。
     *
     * 这里只创建任务，不在 HTTP 请求线程中重建 PDF 向量。
     */
    @Transactional(rollbackFor = Exception.class)
    public CourseEmbeddingMigrationVO start(
            Long userId,
            Long courseId) {

        if (userId == null || userId <= 0
                || courseId == null || courseId <= 0) {

            throw new BusinessException(
                    400, "用户ID或课程ID不合法");
        }

        // 锁定课程，避免同一课程并发创建迁移任务。
        Course course = courseMapper.selectOne(
                new LambdaQueryWrapper<Course>()
                        .eq(Course::getId, courseId)
                        .eq(Course::getUserId, userId)
                        .last("LIMIT 1 FOR UPDATE")
        );

        if (course == null) {
            throw new BusinessException(
                    404, "课程不存在或无权访问");
        }

        // 同一课程不允许同时存在多个活动迁移任务。
        Long activeCount = migrationMapper.selectCount(
                new LambdaQueryWrapper<CourseEmbeddingMigration>()
                        .eq(
                                CourseEmbeddingMigration::getCourseId,
                                courseId
                        )
                        .in(
                                CourseEmbeddingMigration::getStatus,
                                ACTIVE_STATUSES
                        )
        );

        if (activeCount != null && activeCount > 0) {
            throw new BusinessException(
                    409, "该课程已有正在进行的Embedding迁移任务");
        }

        List<CourseDocument> pendingDocuments =
                documentMapper.selectList(
                        new LambdaQueryWrapper<CourseDocument>()
                                .eq(
                                        CourseDocument::getCourseId,
                                        courseId
                                )
                                .eq(
                                        CourseDocument::getVersionStatus,
                                        "PENDING"
                                )
                                .ne(
                                        CourseDocument::getStatus,
                                        "DELETED"
                                )
                                .last("LIMIT 1 FOR UPDATE")
                );

        if (!pendingDocuments.isEmpty()) {

            throw new BusinessException(
                    409,
                    "课程存在尚未发布的资源版本，"
                    + "请完成发布审核或删除待发布版本后再进行迁移"
            );
        }

        // 读取当前用户已经保存的模型配置。
        UserConfig config = userConfigMapper.selectOne(
                new LambdaQueryWrapper<UserConfig>()
                        .eq(UserConfig::getUserId, userId)
        );

        if (config == null
                || config.getEmbeddingProvider() == null
                || config.getEmbeddingBaseUrl() == null
                || config.getEmbeddingModel() == null
                || config.getEmbeddingDim() == null) {

            throw new BusinessException(
                    400, "请先完成Embedding模型配置");
        }

        int sourceVersion =
                course.getEmbeddingVersion() == null
                        ? 1
                        : course.getEmbeddingVersion();

        int targetVersion = sourceVersion + 1;

        // 只迁移当前生效且已完成索引的资源版本。
        long totalDocuments = documentMapper.selectCount(
                new LambdaQueryWrapper<CourseDocument>()
                        .eq(
                                CourseDocument::getCourseId,
                                courseId
                        )
                        .eq(
                                CourseDocument::getStatus,
                                "READY"
                        )
                        .eq(
                                CourseDocument::getVersionStatus,
                                "CURRENT"
                        )
                        .eq(
                                CourseDocument::getReviewStatus,
                                "APPROVED"
                        )
        );

        if (totalDocuments == 0) {
            throw new BusinessException(
                    409, "课程没有可迁移的已完成索引文档");
        }

        if (totalDocuments > 500) {
            throw new BusinessException(
                    400,
                    "当前版本单次最多迁移500篇文档，请先调整迁移批次限制"
            );
        }

        if (config.getEmbeddingDim() <= 0) {
            throw new BusinessException(
                    400,
                    "目标Embedding维度不合法"
            );
        }

        LocalDateTime now = LocalDateTime.now();

        CourseEmbeddingMigration migration =
                new CourseEmbeddingMigration();

        migration.setUserId(userId);
        migration.setCourseId(courseId);

        migration.setSourceVersion(sourceVersion);
        migration.setTargetVersion(targetVersion);

        migration.setTargetProvider(
                config.getEmbeddingProvider());

        migration.setTargetBaseUrl(
                config.getEmbeddingBaseUrl());

        migration.setTargetModel(
                config.getEmbeddingModel());

        migration.setTargetDimension(
                config.getEmbeddingDim());

        // 解密当前用户保存的 Embedding Key。
        String embeddingApiKey =
                config.getEmbeddingApiKeyCiphertext() == null
                        ? ""
                        : cryptoService.decrypt(
                                config.getEmbeddingApiKeyCiphertext()
                        );

        // 保存加密快照，供异步 Worker 使用。
        migration.setTargetApiKeyCiphertext(
                embeddingApiKey.isBlank()
                        ? null
                        : cryptoService.encrypt(embeddingApiKey)
        );

        migration.setTargetApiKeyFingerprint(
                cryptoService.fingerprint(embeddingApiKey)
        );

        // 保存完整模型配置指纹。
        migration.setTargetConfigFingerprint(
                configFingerprint.calculate(
                        config.getEmbeddingProvider(),
                        config.getEmbeddingBaseUrl(),
                        config.getEmbeddingModel(),
                        embeddingApiKey
                )
        );

        migration.setAttemptCount(0);

        migration.setStatus("PENDING");

        migration.setTotalDocuments(
                Math.toIntExact(totalDocuments));

        migration.setProcessedDocuments(0);
        migration.setFailedDocuments(0);

        migration.setCreateTime(now);
        migration.setUpdateTime(now);

        migrationMapper.insert(migration);

        return CourseEmbeddingMigrationVO.from(migration);
    }

    /**
     * 查询当前用户自己的迁移任务。
     */
    public CourseEmbeddingMigrationVO get(
            Long userId,
            Long migrationId) {

        CourseEmbeddingMigration migration =
                migrationMapper.selectOne(
                        new LambdaQueryWrapper<CourseEmbeddingMigration>()
                                .eq(
                                        CourseEmbeddingMigration::getId,
                                        migrationId
                                )
                                .eq(
                                        CourseEmbeddingMigration::getUserId,
                                        userId
                                )
                );

        if (migration == null) {
            throw new BusinessException(
                    404, "迁移任务不存在");
        }

        return CourseEmbeddingMigrationVO.from(migration);
    }

    /**
     * 查询当前用户的所有迁移任务。
     */
    public List<CourseEmbeddingMigrationVO> list(
            Long userId,
            Long courseId) {

        LambdaQueryWrapper<CourseEmbeddingMigration> wrapper =
                new LambdaQueryWrapper<CourseEmbeddingMigration>()
                        .eq(
                                CourseEmbeddingMigration::getUserId,
                                userId
                        )
                        .orderByDesc(
                                CourseEmbeddingMigration::getCreateTime
                        );

        if (courseId != null) {
            wrapper.eq(
                    CourseEmbeddingMigration::getCourseId,
                    courseId
            );
        }

        return migrationMapper.selectList(wrapper)
                .stream()
                .map(CourseEmbeddingMigrationVO::from)
                .toList();
    }
}
