package com.edu.ai.document.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.edu.ai.common.BusinessException;
import com.edu.ai.course.service.CoursePermissionService;
import com.edu.ai.document.entity.CourseDocument;
import com.edu.ai.document.mapper.CourseDocumentMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Set;

@Service
public class DocumentLifecycleService {

    private static final Logger log =
            LoggerFactory.getLogger(
                    DocumentLifecycleService.class);

    private static final Set<String> DELETABLE =
            Set.of("UPLOADED", "READY", "FAILED");

    private final CourseDocumentMapper documentMapper;
    private final CoursePermissionService coursePermissionService;
    private final DocumentIndexTask indexTask;
    private final CourseIndexLease courseIndexLease;
    private final HttpClient httpClient;

    private final String aiUrl;
    private final String internalKey;
    private final Path storageRoot;

    public DocumentLifecycleService(
            CourseDocumentMapper documentMapper,
            CoursePermissionService coursePermissionService,
            DocumentIndexTask indexTask,
            CourseIndexLease courseIndexLease,
            @Value("${ai.service.url}") String aiUrl,
            @Value("${ai.service.internal-key}") String internalKey,
            @Value("${app.storage.pdf-dir}") String pdfDir) {

        this.documentMapper = documentMapper;
        this.coursePermissionService = coursePermissionService;
        this.indexTask = indexTask;
        this.courseIndexLease = courseIndexLease;

        this.aiUrl = aiUrl;
        this.internalKey = internalKey;

        this.storageRoot = Paths.get(pdfDir)
                .toAbsolutePath()
                .normalize();

        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    // 删除文档：上传者或课程创建者可以删除
    public void delete(
            Long documentId,
            Long userId) {

        CourseDocument document =
                requireDocument(documentId);

        requireDocumentManagePermission(document, userId);

        if ("DELETED".equals(document.getStatus())) {
            return;
        }

        if (!DELETABLE.contains(document.getStatus())
                && !"DELETE_FAILED".equals(
                        document.getStatus())) {

            throw new BusinessException(
                    409,
                    "文件正在处理，暂时不能删除"
            );
        }

        Long courseId = document.getCourseId();

        CourseIndexLease.Handle lease =
                courseIndexLease.tryOpen(
                        courseId,
                        "DOCUMENT_DELETE"
                );

        if (lease == null) {
            throw new BusinessException(
                    409,
                    "课程正在索引或迁移，请稍后重试"
            );
        }

        try (lease) {
            // 获得课程锁后重新读取文档状态。
            CourseDocument latest =
                    requireDocument(documentId);

            requireDocumentManagePermission(latest, userId);

            requireVersionDeletable(latest);

            if ("DELETED".equals(latest.getStatus())) {
                return;
            }

            if (!DELETABLE.contains(latest.getStatus())
                    && !"DELETE_FAILED".equals(
                            latest.getStatus())) {

                throw new BusinessException(
                        409,
                        "文件状态已经变化，暂时不能删除"
                );
            }

            int updated = documentMapper.update(
                    null,
                    new LambdaUpdateWrapper<CourseDocument>()
                            .eq(CourseDocument::getId, documentId)
                            .eq(
                                    CourseDocument::getStatus,
                                    latest.getStatus()
                            )
                            .set(
                                    CourseDocument::getStatus,
                                    "DELETING"
                            )
                            .set(
                                    CourseDocument::getErrorMessage,
                                    null
                            )
                            .set(
                                    CourseDocument::getUpdateTime,
                                    LocalDateTime.now()
                            )
            );

            if (updated != 1) {
                throw new BusinessException(
                        409,
                        "文件状态已变化，请重试"
                );
            }

            lease.assertValid();

            cleanup(documentId, lease);
        }
    }

    // 重新处理失败的文档：上传者或课程创建者可以操作
    public void retryIndex(
            Long documentId,
            Long userId) {

        CourseDocument doc = requireDocument(documentId);

        requireDocumentManagePermission(doc, userId);

        if (!"FAILED".equals(doc.getStatus())) {
            throw new BusinessException(
                    409, "只有处理失败的文件才能重新索引");
        }

        Path target = safeFilePath(doc.getStorageName());

        if (!Files.isRegularFile(
                target,
                java.nio.file.LinkOption.NOFOLLOW_LINKS)) {

            throw new BusinessException(
                    404, "原始PDF文件不存在");
        }

        int updated = documentMapper.update(
                null,
                new LambdaUpdateWrapper<CourseDocument>()
                        .eq(CourseDocument::getId, documentId)
                        .eq(CourseDocument::getStatus, "FAILED")
                        .set(CourseDocument::getStatus, "UPLOADED")
                        .set(CourseDocument::getAttemptCount, 0)
                        .set(CourseDocument::getProcessingToken, null)
                        .set(CourseDocument::getProcessingStartedAt, null)
                        .set(CourseDocument::getErrorMessage, null)
                        .set(CourseDocument::getUpdateTime,
                                LocalDateTime.now())
        );

        if (updated != 1) {
            throw new BusinessException(
                    409, "文件状态发生变化，请重试");
        }

        indexTask.process(documentId);
    }

    // 服务重启后恢复未完成的删除操作
    public void resumeDelete(Long documentId) {

        CourseDocument doc =
                documentMapper.selectById(documentId);

        if (doc == null) {
            return;
        }

        if (!"DELETING".equals(doc.getStatus())
                && !"DELETE_FAILED".equals(doc.getStatus())) {
            return;
        }

        Long courseId = doc.getCourseId();

        CourseIndexLease.Handle lease =
                courseIndexLease.tryOpen(
                        courseId,
                        "DOCUMENT_DELETE"
                );

        if (lease == null) {
            return;
        }

        try (lease) {
            resumeDeleteWithLock(documentId, lease);
        }
    }

    private void resumeDeleteWithLock(
            Long documentId,
            CourseIndexLease.Handle lease) {

        CourseDocument doc =
                documentMapper.selectById(documentId);

        if (doc == null) {
            return;
        }

        if ("DELETE_FAILED".equals(doc.getStatus())) {

            int updated = documentMapper.update(
                    null,
                    new LambdaUpdateWrapper<CourseDocument>()
                            .eq(CourseDocument::getId, documentId)
                            .eq(
                                    CourseDocument::getStatus,
                                    "DELETE_FAILED"
                            )
                            .set(
                                    CourseDocument::getStatus,
                                    "DELETING"
                            )
            );

            if (updated != 1) {
                return;
            }

        } else if (!"DELETING".equals(doc.getStatus())) {
            return;
        }

        lease.assertValid();

        try {
            cleanup(documentId, lease);
        } catch (BusinessException ex) {
            log.warn(
                    "Document cleanup recovery failed: {}",
                    documentId
            );
        }
    }

    // 实际清理流程
    private void cleanup(
            Long documentId,
            CourseIndexLease.Handle lease) {

        CourseDocument doc =
                requireDocument(documentId);

        if (!"DELETING".equals(doc.getStatus())) {
            return;
        }

        try {
            // 第一步：通知 Python 删除 ChromaDB 向量
            String body = """
                    {
                      "course_id": %d,
                      "document_id": %d
                    }
                    """.formatted(
                    doc.getCourseId(),
                    doc.getId()
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(
                            URI.create(
                                    aiUrl
                                            + "/internal/documents/delete"
                            )
                    )
                    .timeout(Duration.ofMinutes(2))
                    .header(
                            "Content-Type",
                            "application/json"
                    )
                    .header(
                            "X-Internal-Key",
                            internalKey
                    )
                    .POST(
                            HttpRequest.BodyPublishers.ofString(body)
                    )
                    .build();

            HttpResponse<String> response =
                    httpClient.send(
                            request,
                            HttpResponse.BodyHandlers.ofString()
                    );

            if (response.statusCode() != 200) {
                throw new IllegalStateException(
                        "Python deletion HTTP "
                                + response.statusCode()
                );
            }

            // Python 删除成功后，再次检查课程锁。
            lease.assertValid();

            // 第二步：删除磁盘上的 PDF
            Path filePath = safeFilePath(
                    doc.getStorageName());

            Files.deleteIfExists(filePath);

            // 删除 PDF 后、更新状态前，再次检查课程锁。
            lease.assertValid();

            // 第三步：保留数据库记录，标记删除完成
            documentMapper.update(
                    null,
                    new LambdaUpdateWrapper<CourseDocument>()
                            .eq(CourseDocument::getId, documentId)
                            .eq(
                                    CourseDocument::getStatus,
                                    "DELETING"
                            )
                            .set(
                                    CourseDocument::getStatus,
                                    "DELETED"
                            )
                            .set(
                                    CourseDocument::getErrorMessage,
                                    null
                            )
                            .set(
                                    CourseDocument::getUpdateTime,
                                    LocalDateTime.now()
                            )
            );

            log.info(
                    "Document deleted successfully: {}",
                    documentId
            );

        } catch (Exception e) {

            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }

            log.error(
                    "Document deletion failed: {}",
                    documentId,
                    e
            );

            documentMapper.update(
                    null,
                    new LambdaUpdateWrapper<CourseDocument>()
                            .eq(CourseDocument::getId, documentId)
                            .eq(
                                    CourseDocument::getStatus,
                                    "DELETING"
                            )
                            .set(
                                    CourseDocument::getStatus,
                                    "DELETE_FAILED"
                            )
                            .set(
                                    CourseDocument::getErrorMessage,
                                    "文件删除失败，等待恢复"
                            )
                            .set(
                                    CourseDocument::getUpdateTime,
                                    LocalDateTime.now()
                            )
            );

            throw new BusinessException(
                    503,
                    "文件删除暂时失败，系统将尝试恢复"
            );
        }
    }

    private CourseDocument requireDocument(Long id) {

        CourseDocument doc =
                documentMapper.selectById(id);

        if (doc == null) {
            throw new BusinessException(
                    404, "文档不存在"
            );
        }

        return doc;
    }

    /**
     * 文档管理权限：课程创建教师或管理员。
     */
    private void requireDocumentManagePermission(
            CourseDocument document,
            Long userId) {

        coursePermissionService.requireManage(
                document.getCourseId(),
                userId
        );
    }

    /**
     * 防止普通删除操作破坏资源版本历史。
     */
    private void requireVersionDeletable(
            CourseDocument document) {

        // 历史版本需要保留，不能直接通过普通删除接口删除。
        if ("HISTORY".equals(document.getVersionStatus())) {

            throw new BusinessException(
                    409,
                    "历史版本需要保留，不能直接删除"
            );
        }

        // 待处理版本可以沿用原有删除逻辑。
        if ("PENDING".equals(document.getVersionStatus())) {
            return;
        }

        // 已经存在其他版本的资源不能单独删除当前版本，
        // 否则可能导致资源失去生效版本。
        if ("CURRENT".equals(document.getVersionStatus())
                && document.getResourceId() != null) {

            Long count = documentMapper.selectCount(
                    new LambdaQueryWrapper<CourseDocument>()
                            .eq(
                                    CourseDocument::getResourceId,
                                    document.getResourceId()
                            )
                            .eq(
                                    CourseDocument::getCourseId,
                                    document.getCourseId()
                            )
                            .ne(
                                    CourseDocument::getId,
                                    document.getId()
                            )
                            .ne(
                                    CourseDocument::getStatus,
                                    "DELETED"
                            )
            );

            if (count != null && count > 0) {

                throw new BusinessException(
                        409,
                        "该资源存在其他版本，暂不支持单独删除当前版本"
                );
            }
        }
    }

    private Path safeFilePath(String storageName) {

        if (storageName == null
                || !storageName.matches(
                        "[0-9a-fA-F]{8}-"
                        + "[0-9a-fA-F]{4}-"
                        + "[0-9a-fA-F]{4}-"
                        + "[0-9a-fA-F]{4}-"
                        + "[0-9a-fA-F]{12}\\.pdf"
                )) {

            throw new BusinessException(
                    500,
                    "文件存储名称不合法"
            );
        }

        Path target = storageRoot
                .resolve(storageName)
                .normalize();

        if (!target.startsWith(storageRoot)) {
            throw new BusinessException(
                    403,
                    "非法文件路径"
            );
        }

        return target;
    }
}
