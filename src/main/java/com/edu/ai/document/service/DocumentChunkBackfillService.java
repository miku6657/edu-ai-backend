package com.edu.ai.document.service;

import com.edu.ai.common.BusinessException;

import com.edu.ai.course.entity.Course;
import com.edu.ai.course.service.CoursePermissionService;

import com.edu.ai.document.entity.CourseDocument;
import com.edu.ai.document.entity.CourseEmbeddingProfile;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import tools.jackson.databind.JsonNode;

import java.util.List;

@Service
public class DocumentChunkBackfillService {

    private final JdbcTemplate jdbc;

    private final DocumentChunkService chunkService;

    private final CoursePermissionService permissions;

    private final CourseEmbeddingProfileService profileService;

    private final CourseIndexLease courseIndexLease;

    private final CourseIndexLockService lockService;

    private final AiDocumentChunkClient aiClient;

    private final TransactionTemplate transactionTemplate;

    public DocumentChunkBackfillService(
            JdbcTemplate jdbc,
            DocumentChunkService chunkService,
            CoursePermissionService permissions,
            CourseEmbeddingProfileService profileService,
            CourseIndexLease courseIndexLease,
            CourseIndexLockService lockService,
            AiDocumentChunkClient aiClient,
            TransactionTemplate transactionTemplate) {

        this.jdbc = jdbc;
        this.chunkService = chunkService;
        this.permissions = permissions;
        this.profileService = profileService;
        this.courseIndexLease = courseIndexLease;
        this.lockService = lockService;
        this.aiClient = aiClient;
        this.transactionTemplate = transactionTemplate;
    }

    public record BackfillResult(

            String documentId,

            long chunkCount,

            boolean alreadyInitialized

    ) {}

    /**
     * 补录当前生效文档的历史知识切片。
     */
    public BackfillResult backfill(
            Long courseId,
            Long documentId,
            Long userId) {

        chunkService.requireManagedDocument(
                courseId,
                documentId,
                userId
        );

        CourseIndexLease.Handle lease =
                courseIndexLease.tryOpen(
                        courseId,
                        "CHUNK_BACKFILL"
                );

        if (lease == null) {

            throw new BusinessException(
                    409,
                    "课程正在索引、迁移或删除资料，请稍后重试"
            );
        }

        try (lease) {

            CourseDocument document =
                    chunkService.requireManagedDocument(
                            courseId,
                            documentId,
                            userId
                    );

            // 已经补录过的文档直接返回。
            long existingCount =
                    chunkService.countChunks(
                            courseId,
                            documentId
                    );

            if (existingCount > 0) {

                return new BackfillResult(
                        documentId.toString(),
                        existingCount,
                        true
                );
            }

            if (!"CURRENT".equals(
                    document.getVersionStatus())) {

                throw new BusinessException(
                        409,
                        "仅支持补录当前生效版本的历史切片"
                );
            }

            Course course =
                    permissions.requireManage(
                            courseId,
                            userId
                    );

            int embeddingVersion =
                    course.getEmbeddingVersion() == null
                            ? 1
                            : course.getEmbeddingVersion();

            CourseEmbeddingProfile profile =
                    profileService.require(
                            courseId,
                            embeddingVersion
                    );

            int chunkingVersion =
                    document.getChunkingVersion() == null
                            ? 1
                            : document.getChunkingVersion();

            lease.assertValid();

            // 读取ChromaDB，不修改任何向量。
            JsonNode result = aiClient.readChunks(
                    courseId,
                    documentId,
                    profile.getCollectionName(),
                    chunkingVersion
            );

            List<DocumentChunkService.ChunkData>
                    chunks =
                    chunkService.parseIndexResult(
                            documentId,
                            chunkingVersion,
                            result
                    );

            lease.assertValid();

            // 远程调用完成后再开启数据库事务，
            // 避免长时间持有数据库行锁。
            return transactionTemplate.execute(
                    status -> {

                        // 锁定课程索引锁记录并再次校验。
                        lockService.assertOwnedForUpdate(
                                courseId,
                                lease.getToken()
                        );

                        permissions.requireManage(
                                courseId,
                                userId
                        );

                        List<Integer> versions =
                                jdbc.queryForList(
                                        """
                                        SELECT COALESCE(
                                            embedding_version,
                                            1
                                        )
                                        FROM course
                                        WHERE id = ?
                                          AND status = 1
                                        FOR UPDATE
                                        """,
                                        Integer.class,
                                        courseId
                                );

                        if (versions.isEmpty()
                                || versions.get(0)
                                   != embeddingVersion) {

                            throw new BusinessException(
                                    409,
                                    "课程索引版本已经变化，请重新补录"
                            );
                        }

                        List<Long> documents =
                                jdbc.queryForList(
                                        """
                                        SELECT id
                                        FROM course_document
                                        WHERE id = ?
                                          AND course_id = ?
                                          AND status = 'READY'
                                          AND version_status = 'CURRENT'
                                          AND chunking_version = ?
                                        FOR UPDATE
                                        """,
                                        Long.class,
                                        documentId,
                                        courseId,
                                        chunkingVersion
                                );

                        if (documents.isEmpty()) {

                            throw new BusinessException(
                                    409,
                                    "文档版本已经变化，请刷新后重试"
                            );
                        }

                        long count =
                                chunkService.countChunks(
                                        courseId,
                                        documentId
                                );

                        if (count > 0) {

                            return new BackfillResult(
                                    documentId.toString(),
                                    count,
                                    true
                            );
                        }

                        chunkService.saveIndexedChunks(
                                courseId,
                                documentId,
                                chunks
                        );

                        return new BackfillResult(
                                documentId.toString(),
                                chunks.size(),
                                false
                        );
                    }
            );
        }
    }
}
