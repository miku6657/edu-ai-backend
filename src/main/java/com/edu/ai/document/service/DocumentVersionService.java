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

    public DocumentVersionService(
            JdbcTemplate jdbc,
            CoursePermissionService permissions) {

        this.jdbc = jdbc;
        this.permissions = permissions;
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
     * 将指定文档标记为READY，
     * 并切换为当前生效版本。
     *
     * 必须在Python索引成功、
     * 且索引任务仍持有有效令牌后调用。
     */
    @Transactional(rollbackFor = Exception.class)
    public void finishIndexAndActivate(
            Long documentId,
            String processingToken) {

        if (processingToken == null
                || processingToken.isBlank()) {

            throw new IllegalArgumentException(
                    "索引任务令牌不能为空"
            );
        }

        VersionDocument document =
                requireDocument(documentId);

        // 统一锁定资源根记录，
        // 保证同一资源的版本切换串行执行。
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
                    409, "资源根记录不存在"
            );
        }

        // 确认本次索引任务仍然有效。
        int finished = jdbc.update(
                """
                UPDATE course_document
                SET status = 'READY',
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
                    409, "文档索引任务状态已经变化"
            );
        }

        // 如果较新的版本已经生效，
        // 较旧版本即使较晚完成索引，也不能覆盖新版本。
        List<Integer> activeVersions =
                jdbc.queryForList(
                        """
                        SELECT version_no
                        FROM course_document
                        WHERE resource_id = ?
                          AND course_id = ?
                          AND version_status = 'CURRENT'
                        FOR UPDATE
                        """,
                        Integer.class,
                        document.resourceId(),
                        document.courseId()
                );

        boolean newerVersionExists =
                activeVersions.stream()
                        .anyMatch(version ->
                                version > document.versionNo()
                        );

        if (newerVersionExists) {

            jdbc.update(
                    """
                    UPDATE course_document
                    SET version_status = 'HISTORY',
                        update_time = NOW()
                    WHERE id = ?
                    """,
                    documentId
            );

            return;
        }

        // 原生效版本转为历史版本。
        jdbc.update(
                """
                UPDATE course_document
                SET version_status = 'HISTORY',
                    update_time = NOW()
                WHERE resource_id = ?
                  AND course_id = ?
                  AND version_status = 'CURRENT'
                  AND id <> ?
                """,
                document.resourceId(),
                document.courseId(),
                documentId
        );

        // 新版本正式生效。
        int activated = jdbc.update(
                """
                UPDATE course_document
                SET version_status = 'CURRENT',
                    update_time = NOW()
                WHERE id = ?
                  AND status = 'READY'
                  AND version_status = 'PENDING'
                """,
                documentId
        );

        if (activated != 1) {
            throw new BusinessException(
                    409, "资源版本切换失败"
            );
        }
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
