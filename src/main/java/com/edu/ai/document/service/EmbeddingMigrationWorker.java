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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

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
    private final EmbeddingMigrationLeaseService leaseService;
    private final CourseIndexLockService indexLockService;
    private final TransactionTemplate transactionTemplate;

    private final ScheduledExecutorService heartbeatExecutor =
            Executors.newScheduledThreadPool(2);

    private final Map<Long, AtomicBoolean> leaseLost =
            new ConcurrentHashMap<>();

    public EmbeddingMigrationWorker(
            CourseEmbeddingMigrationMapper migrationMapper,
            CourseDocumentMapper documentMapper,
            CourseMapper courseMapper,
            AiEmbeddingMigrationClient aiClient,
            ConfigCryptoService cryptoService,
            EmbeddingMigrationLeaseService leaseService,
            CourseIndexLockService indexLockService,
            TransactionTemplate transactionTemplate) {

        this.migrationMapper = migrationMapper;
        this.documentMapper = documentMapper;
        this.courseMapper = courseMapper;
        this.aiClient = aiClient;
        this.cryptoService = cryptoService;
        this.leaseService = leaseService;
        this.indexLockService = indexLockService;
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
     * 每分钟恢复一次租约过期的任务。
     */
    @Scheduled(fixedDelay = 60000)
    public void recoverExpiredTasks() {

        int recovered = leaseService.recoverExpired();

        if (recovered > 0) {
            log.warn(
                    "Recovered expired embedding migrations: {}",
                    recovered
            );
        }
    }

    /**
     * 原子领取任务，同时获得执行令牌和租约。
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
                                    CourseEmbeddingMigration::getLeaseUntil,
                                    now.plusMinutes(35)
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
        Long courseId = migration.getCourseId();
        String token = migration.getProcessingToken();

        // 先获取课程索引锁：迁移期间独占课程。
        String courseLockToken = indexLockService.tryAcquire(
                courseId,
                "EMBEDDING_MIGRATION"
        );

        // 课程正在索引或删除，不执行迁移。
        if (courseLockToken == null) {
            markFailedOrRetry(
                    migration,
                    token,
                    new IllegalStateException(
                            "课程正在执行其他索引操作，等待重试"
                    )
            );
            return;
        }

        AtomicBoolean lost = new AtomicBoolean(false);
        leaseLost.put(migrationId, lost);

        ScheduledFuture<?> heartbeat = null;

        try {
            // 每分钟续租迁移任务租约。
            heartbeat = heartbeatExecutor.scheduleAtFixedRate(
                    () -> {
                        try {
                            if (!leaseService.renew(migrationId, token)) {
                                lost.set(true);
                                log.warn(
                                        "Migration lease lost: {}",
                                        migrationId
                                );
                            }
                        } catch (Exception e) {
                            lost.set(true);
                            log.error(
                                    "Migration lease renewal failed: {}",
                                    migrationId,
                                    e
                            );
                        }
                    },
                    1,
                    1,
                    TimeUnit.MINUTES
            );

            // 确认迁移任务仍然属于当前 Worker。
            CourseEmbeddingMigration current =
                    migrationMapper.selectOne(
                            new LambdaQueryWrapper<CourseEmbeddingMigration>()
                                    .eq(
                                            CourseEmbeddingMigration::getId,
                                            migrationId
                                    )
                                    .eq(
                                            CourseEmbeddingMigration::getStatus,
                                            "RUNNING"
                                    )
                                    .eq(
                                            CourseEmbeddingMigration::getProcessingToken,
                                            token
                                    )
                                    .last("LIMIT 1")
                    );

            if (current == null) {
                return;
            }

            if (!indexLockService.isOwned(
                    courseId,
                    courseLockToken)) {
                throw new IllegalStateException(
                        "课程索引锁已经失效"
                );
            }

            List<CourseDocument> documents =
                    documentMapper.selectReadyByCourseId(courseId);

            if (documents.isEmpty()) {
                throw new IllegalStateException(
                        "课程没有可迁移的已完成索引文档"
                );
            }

            // 记录迁移开始时的文档集合指纹。
            List<Long> originalIds =
                    documentMapper.selectReadyDocumentIds(courseId);

            String originalFingerprint =
                    documentSetFingerprint(originalIds);

            if (documents.size() != migration.getTotalDocuments()) {
                throw new IllegalStateException(
                        "课程文档数量发生变化"
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

            // Python 完成后，重新检查租约和课程锁。
            if (lost.get()) {
                throw new IllegalStateException(
                        "迁移租约已经丢失，禁止切换课程版本"
                );
            }

            CourseEmbeddingMigration latest =
                    migrationMapper.selectById(migrationId);

            if (latest == null
                    || !"RUNNING".equals(latest.getStatus())
                    || !token.equals(latest.getProcessingToken())) {

                throw new IllegalStateException(
                        "迁移任务执行令牌已经失效"
                );
            }

            if (!indexLockService.isOwned(
                    courseId,
                    courseLockToken)) {

                throw new IllegalStateException(
                        "课程索引锁已经失效，禁止切换版本"
                );
            }

            // 重新校验文档集合未发生变化。
            List<Long> latestIds =
                    documentMapper.selectReadyDocumentIds(courseId);

            String latestFingerprint =
                    documentSetFingerprint(latestIds);

            if (!originalFingerprint.equals(latestFingerprint)) {
                throw new IllegalStateException(
                        "迁移期间课程文档集合发生变化"
                );
            }

            validateResult(migration, documents, result);

            switchCourseVersion(
                    migration,
                    token,
                    documents.size(),
                    courseLockToken
            );

        } catch (Exception e) {

            log.error(
                    "Embedding migration failed, migrationId={}, courseId={}",
                    migrationId,
                    courseId,
                    e
            );

            if (!lost.get()) {
                markFailedOrRetry(
                        migration,
                        token,
                        e
                );
            }

        } finally {

            if (heartbeat != null) {
                heartbeat.cancel(false);
            }

            leaseLost.remove(migrationId);

            // 释放课程索引锁。
            indexLockService.release(courseId, courseLockToken);

            // 仅在未丢租约时释放迁移任务租约；
            // 丢租约时留给 recoverExpiredLeases 回收，避免任务卡死。
            if (!lost.get()) {
                leaseService.release(migrationId, token);
            }
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
            int processedDocuments,
            String courseLockToken) {

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

            // 切换版本前再次确认课程锁仍有效。
            if (!indexLockService.isOwned(
                    migration.getCourseId(),
                    courseLockToken)) {

                throw new IllegalStateException(
                        "课程索引锁失效，禁止切换版本"
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
            current.setLeaseUntil(null);
            current.setNextRetryAt(null);
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

    private String documentSetFingerprint(
            List<Long> documentIds) {

        try {
            String value = documentIds.stream()
                    .sorted()
                    .map(String::valueOf)
                    .collect(java.util.stream.Collectors.joining(","));

            byte[] digest = java.security.MessageDigest
                    .getInstance("SHA-256")
                    .digest(
                            value.getBytes(
                                    java.nio.charset.StandardCharsets.UTF_8
                            )
                    );

            return java.util.HexFormat.of().formatHex(digest);

        } catch (Exception e) {
            throw new IllegalStateException(
                    "文档集合指纹计算失败",
                    e
            );
        }
    }
}
