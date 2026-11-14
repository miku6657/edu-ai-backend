package com.edu.ai.document.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;

import com.edu.ai.course.entity.Course;
import com.edu.ai.course.mapper.CourseMapper;

import com.edu.ai.document.entity.CourseDocument;
import com.edu.ai.document.entity.CourseEmbeddingMigration;

import com.edu.ai.document.mapper.CourseDocumentMapper;
import com.edu.ai.document.mapper.CourseEmbeddingMigrationMapper;

import com.edu.ai.system.security.ConfigCryptoService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Component
public class EmbeddingMigrationWorker {

    private static final Logger log =
            LoggerFactory.getLogger(EmbeddingMigrationWorker.class);

    private static final int MAX_ATTEMPTS = 3;

    private final CourseEmbeddingMigrationMapper migrationMapper;
    private final CourseDocumentMapper documentMapper;
    private final CourseMapper courseMapper;

    private final AiEmbeddingMigrationClient aiClient;
    private final ConfigCryptoService cryptoService;
    private final TransactionTemplate transactionTemplate;

    public EmbeddingMigrationWorker(
            CourseEmbeddingMigrationMapper migrationMapper,
            CourseDocumentMapper documentMapper,
            CourseMapper courseMapper,
            AiEmbeddingMigrationClient aiClient,
            ConfigCryptoService cryptoService,
            TransactionTemplate transactionTemplate) {

        this.migrationMapper = migrationMapper;
        this.documentMapper = documentMapper;
        this.courseMapper = courseMapper;
        this.aiClient = aiClient;
        this.cryptoService = cryptoService;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * 每5秒检查一次待迁移任务。
     */
    @Scheduled(fixedDelay = 5000)
    public void poll() {

        for (int i = 0; i < 3; i++) {

            CourseEmbeddingMigration migration =
                    claimOne();

            if (migration == null) {
                break;
            }

            execute(migration);
        }
    }

    /**
     * 原子领取任务。
     */
    private CourseEmbeddingMigration claimOne() {

        return transactionTemplate.execute(status -> {

            LocalDateTime now = LocalDateTime.now();

            CourseEmbeddingMigration migration =
                    migrationMapper.selectOne(
                            new LambdaQueryWrapper<CourseEmbeddingMigration>()
                                    .eq(
                                            CourseEmbeddingMigration::getStatus,
                                            "PENDING"
                                    )
                                    .and(w -> w
                                            .isNull(
                                                    CourseEmbeddingMigration::getNextRetryAt
                                            )
                                            .or()
                                            .le(
                                                    CourseEmbeddingMigration::getNextRetryAt,
                                                    now
                                            )
                                    )
                                    .orderByAsc(
                                            CourseEmbeddingMigration::getCreateTime
                                    )
                                    .last("LIMIT 1 FOR UPDATE")
                    );

            if (migration == null) {
                return null;
            }

            String token = UUID.randomUUID().toString();

            int updated = migrationMapper.update(
                    null,
                    new LambdaUpdateWrapper<CourseEmbeddingMigration>()
                            .eq(
                                    CourseEmbeddingMigration::getId,
                                    migration.getId()
                            )
                            .eq(
                                    CourseEmbeddingMigration::getStatus,
                                    "PENDING"
                            )
                            .set(
                                    CourseEmbeddingMigration::getStatus,
                                    "RUNNING"
                            )
                            .set(
                                    CourseEmbeddingMigration::getProcessingToken,
                                    token
                            )
                            .set(
                                    CourseEmbeddingMigration::getStartedAt,
                                    now
                            )
                            .set(
                                    CourseEmbeddingMigration::getUpdateTime,
                                    now
                            )
                            .setSql(
                                    "attempt_count = attempt_count + 1"
                            )
            );

            return updated == 1 ? migrationMapper.selectById(
                    migration.getId()
            ) : null;
        });
    }

    /**
     * 执行迁移。
     */
    private void execute(
            CourseEmbeddingMigration migration) {

        Long migrationId = migration.getId();
        String token = migration.getProcessingToken();

        try {

            List<CourseDocument> documents =
                    documentMapper.selectReadyByCourseId(
                            migration.getCourseId()
                    );

            if (documents.isEmpty()) {
                throw new IllegalStateException(
                        "课程没有可迁移的已完成索引文档"
                );
            }

            if (documents.size()
                    != migration.getTotalDocuments()) {

                throw new IllegalStateException(
                        "迁移期间课程文档发生变化，请重新创建迁移任务"
                );
            }

            String apiKey =
                    migration.getTargetApiKeyCiphertext() == null
                            ? ""
                            : cryptoService.decrypt(
                                    migration.getTargetApiKeyCiphertext()
                            );

            AiEmbeddingMigrationClient.MigrationResult result =
                    aiClient.migrate(
                            migration,
                            documents,
                            apiKey
                    );

            validateResult(migration, documents, result);

            switchCourseVersion(
                    migration,
                    token,
                    documents.size()
            );

        } catch (Exception e) {

            log.error(
                    "Embedding migration failed, migrationId={}, courseId={}",
                    migrationId,
                    migration.getCourseId(),
                    e
            );

            markFailedOrRetry(
                    migration,
                    token,
                    e
            );
        }
    }

    /**
     * 验证 Python 返回的迁移结果。
     */
    private void validateResult(
            CourseEmbeddingMigration migration,
            List<CourseDocument> documents,
            AiEmbeddingMigrationClient.MigrationResult result) {

        if (result == null
                || !"SUCCEEDED".equals(result.status)) {

            throw new IllegalStateException(
                    "迁移结果状态不正确"
            );
        }

        if (result.courseId != migration.getCourseId()
                || result.sourceVersion
                != migration.getSourceVersion()
                || result.targetVersion
                != migration.getTargetVersion()) {

            throw new IllegalStateException(
                    "迁移结果版本或课程ID不匹配"
            );
        }

        if (result.totalDocuments != documents.size()) {
            throw new IllegalStateException(
                    "迁移文档数量不匹配"
            );
        }

        if (result.dimension
                != migration.getTargetDimension()) {

            throw new IllegalStateException(
                    "迁移向量维度不匹配"
            );
        }

        if (result.indexedChunks <= 0) {
            throw new IllegalStateException(
                    "迁移没有生成有效向量"
            );
        }
    }

    /**
     * 只有迁移成功并验证后才切换课程版本。
     */
    private void switchCourseVersion(
            CourseEmbeddingMigration migration,
            String token,
            int processedDocuments) {

        transactionTemplate.executeWithoutResult(status -> {

            CourseEmbeddingMigration current =
                    migrationMapper.selectOne(
                            new LambdaQueryWrapper<CourseEmbeddingMigration>()
                                    .eq(
                                            CourseEmbeddingMigration::getId,
                                            migration.getId()
                                    )
                                    .eq(
                                            CourseEmbeddingMigration::getStatus,
                                            "RUNNING"
                                    )
                                    .eq(
                                            CourseEmbeddingMigration::getProcessingToken,
                                            token
                                    )
                                    .last("LIMIT 1 FOR UPDATE")
                    );

            if (current == null) {
                throw new IllegalStateException(
                        "迁移任务已经失效，不能切换版本"
                );
            }

            Course course = courseMapper.selectOne(
                    new LambdaQueryWrapper<Course>()
                            .eq(
                                    Course::getId,
                                    migration.getCourseId()
                            )
                            .eq(
                                    Course::getUserId,
                                    migration.getUserId()
                            )
                            .eq(
                                    Course::getEmbeddingVersion,
                                    migration.getSourceVersion()
                            )
                            .last("LIMIT 1 FOR UPDATE")
            );

            if (course == null) {
                throw new IllegalStateException(
                        "课程版本已经发生变化，不能切换"
                );
            }

            // 原子更新课程当前生效版本。
            int changed = courseMapper.update(
                    null,
                    new LambdaUpdateWrapper<Course>()
                            .eq(
                                    Course::getId,
                                    migration.getCourseId()
                            )
                            .eq(
                                    Course::getEmbeddingVersion,
                                    migration.getSourceVersion()
                            )
                            .set(
                                    Course::getEmbeddingVersion,
                                    migration.getTargetVersion()
                            )
            );

            if (changed != 1) {
                throw new IllegalStateException(
                        "课程版本切换失败"
                );
            }

            current.setStatus("SUCCEEDED");
            current.setProcessedDocuments(processedDocuments);
            current.setFailedDocuments(0);
            current.setFinishedAt(LocalDateTime.now());
            current.setProcessingToken(null);
            current.setUpdateTime(LocalDateTime.now());

            migrationMapper.updateById(current);
        });
    }

    /**
     * 失败时保留旧版本。
     */
    private void markFailedOrRetry(
            CourseEmbeddingMigration migration,
            String token,
            Exception error) {

        transactionTemplate.executeWithoutResult(status -> {

            CourseEmbeddingMigration current =
                    migrationMapper.selectOne(
                            new LambdaQueryWrapper<CourseEmbeddingMigration>()
                                    .eq(
                                            CourseEmbeddingMigration::getId,
                                            migration.getId()
                                    )
                                    .eq(
                                            CourseEmbeddingMigration::getStatus,
                                            "RUNNING"
                                    )
                                    .eq(
                                            CourseEmbeddingMigration::getProcessingToken,
                                            token
                                    )
                                    .last("LIMIT 1 FOR UPDATE")
                    );

            if (current == null) {
                return;
            }

            int attempts = current.getAttemptCount() == null
                    ? 1
                    : current.getAttemptCount();

            boolean retry = attempts < MAX_ATTEMPTS;

            current.setStatus(
                    retry ? "PENDING" : "FAILED"
            );

            current.setErrorMessage(
                    safeErrorMessage(error)
            );

            current.setProcessingToken(null);

            current.setNextRetryAt(
                    retry
                            ? LocalDateTime.now().plusMinutes(
                                    (long) attempts * 2
                            )
                            : null
            );

            if (!retry) {
                current.setFinishedAt(LocalDateTime.now());
                current.setFailedDocuments(
                        current.getTotalDocuments()
                );
            }

            current.setUpdateTime(LocalDateTime.now());

            migrationMapper.updateById(current);
        });
    }

    private String safeErrorMessage(Exception error) {

        String message = error.getMessage();

        if (message == null || message.isBlank()) {
            return "Embedding迁移执行失败";
        }

        // 避免把可能包含敏感请求信息的长异常直接写入数据库。
        return message.length() > 500
                ? message.substring(0, 500)
                : message;
    }
}
