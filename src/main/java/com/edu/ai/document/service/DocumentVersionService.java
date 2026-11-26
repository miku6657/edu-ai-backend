package com.edu.ai.document.service;

import com.edu.ai.common.BusinessException;
import com.edu.ai.course.service.CoursePermissionService;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class DocumentVersionService {

    private final JdbcTemplate jdbc;
    private final CoursePermissionService permissions;
    private final DocumentChunkService chunkService;

    public DocumentVersionService(
            JdbcTemplate jdbc,
            CoursePermissionService permissions,
            DocumentChunkService chunkService) {

        this.jdbc = jdbc;
        this.permissions = permissions;
        this.chunkService = chunkService;
    }

    public record VersionInfo(
            String documentId,
            String resourceId,
            int versionNo,
            String originalName,
            String versionStatus,
            String status,
            String uploaderId,
            LocalDateTime createTime
    ) {}

    private record VersionDocument(
            long id,
            long courseId,
            long resourceId,
            int versionNo,
            String versionStatus,
            String status
    ) {}

    /**
     * 查询资源历史版本。
     * 仅课程管理者可查看完整版本历史。
     */
    public List<VersionInfo> listVersions(
            Long documentId,
            Long userId) {

        VersionDocument document =
                requireDocument(documentId);

        permissions.requireManage(
                document.courseId(),
                userId
        );

        return jdbc.query(
                """
                SELECT
                    id,
                    resource_id,
                    version_no,
                    original_name,
                    version_status,
                    status,
                    uploader_id,
                    create_time
                FROM course_document
                WHERE resource_id = ?
                  AND course_id = ?
                  AND status NOT IN (
                      'DELETING',
                      'DELETE_FAILED',
                      'DELETED'
                  )
                ORDER BY version_no DESC
                """,
                (rs, n) -> new VersionInfo(
                        Long.toString(
                                rs.getLong("id")
                        ),
                        Long.toString(
                                rs.getLong("resource_id")
                        ),
                        rs.getInt("version_no"),
                        rs.getString("original_name"),
                        rs.getString("version_status"),
                        rs.getString("status"),
                        Long.toString(
                                rs.getLong("uploader_id")
                        ),
                        rs.getTimestamp("create_time")
                                .toLocalDateTime()
                ),
                document.resourceId(),
                document.courseId()
        );
    }

    /**
     * 完成文档索引及切片持久化。
     *
     * F2-04修改：
     * 索引完成后不再自动激活文档版本，
     * 而是等待人工审核发布。
     */
    @Transactional(rollbackFor = Exception.class)
    public void finishIndexAndActivate(

            Long documentId,

            String processingToken,

            List<DocumentChunkService.ChunkData> chunks) {

        if (processingToken == null
                || processingToken.isBlank()) {

            throw new IllegalArgumentException(
                    "索引任务令牌不能为空"
            );
        }

        VersionDocument document =
                requireDocument(documentId);

        // 锁定课程，协调知识点维护与审核操作。
        List<Integer> courseStatuses =
                jdbc.queryForList(
                        """
                        SELECT status
                        FROM course
                        WHERE id = ?
                        FOR UPDATE
                        """,
                        Integer.class,
                        document.courseId()
                );

        if (courseStatuses.isEmpty()
                || !Integer.valueOf(1)
                           .equals(courseStatuses.get(0))) {

            throw new BusinessException(
                    409,
                    "课程当前不可用"
            );
        }

        // 锁定资源根记录。
        List<Long> roots = jdbc.queryForList(
                """
                SELECT id
                FROM course_document
                WHERE id = ?
                  AND course_id = ?
                FOR UPDATE
                """,
                Long.class,
                document.resourceId(),
                document.courseId()
        );

        if (roots.isEmpty()) {

            throw new BusinessException(
                    409,
                    "资源根记录不存在"
            );
        }

        // 索引完成，但仍然保持待生效状态。
        int finished = jdbc.update(
                """
                UPDATE course_document
                SET status = 'READY',
                    review_status = 'DRAFT',
                    review_submitter_id = NULL,
                    review_submit_time = NULL,
                    reviewer_id = NULL,
                    review_time = NULL,
                    review_comment = NULL,
                    processing_token = NULL,
                    processing_started_at = NULL,
                    error_message = NULL,
                    update_time = NOW()
                WHERE id = ?
                  AND resource_id = ?
                  AND status = 'PROCESSING'
                  AND processing_token = ?
                  AND version_status = 'PENDING'
                """,
                documentId,
                document.resourceId(),
                processingToken
        );

        if (finished != 1) {

            throw new BusinessException(
                    409,
                    "文档索引任务状态已经变化"
            );
        }

        // 保存Python索引实际使用的全部知识切片。
        chunkService.saveIndexedChunks(

                document.courseId(),

                documentId,

                chunks
        );

        // 注意：
        // 不再将原版本修改为HISTORY。
        // 不再将当前文档修改为CURRENT。
        //
        // 版本激活由DocumentReviewService负责。
    }

    private VersionDocument requireDocument(
            Long documentId) {

        List<VersionDocument> rows = jdbc.query(
                """
                SELECT
                    id,
                    course_id,
                    resource_id,
                    version_no,
                    version_status,
                    status
                FROM course_document
                WHERE id = ?
                """,
                (rs, n) -> new VersionDocument(
                        rs.getLong("id"),
                        rs.getLong("course_id"),
                        rs.getLong("resource_id"),
                        rs.getInt("version_no"),
                        rs.getString("version_status"),
                        rs.getString("status")
                ),
                documentId
        );

        if (rows.isEmpty()) {
            throw new BusinessException(
                    404, "资源文档不存在"
            );
        }

        VersionDocument doc = rows.get(0);

        if (doc.resourceId() <= 0) {
            throw new BusinessException(
                    409, "资源尚未完成版本初始化"
            );
        }

        return doc;
    }
}
