package com.edu.ai.document.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.edu.ai.common.BusinessException;
import com.edu.ai.course.entity.Course;
import com.edu.ai.course.mapper.CourseMapper;
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
    private final CourseMapper courseMapper;
    private final DocumentIndexTask indexTask;
    private final HttpClient httpClient;

    private final String aiUrl;
    private final String internalKey;
    private final Path storageRoot;

    public DocumentLifecycleService(
            CourseDocumentMapper documentMapper,
            CourseMapper courseMapper,
            DocumentIndexTask indexTask,
            @Value("${ai.service.url}") String aiUrl,
            @Value("${ai.service.internal-key}") String internalKey,
            @Value("${app.storage.pdf-dir}") String pdfDir) {

        this.documentMapper = documentMapper;
        this.courseMapper = courseMapper;
        this.indexTask = indexTask;

        this.aiUrl = aiUrl;
        this.internalKey = internalKey;

        this.storageRoot = Paths.get(pdfDir)
                .toAbsolutePath()
                .normalize();

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    // 负责人删除 PDF
    public synchronized void delete(
            Long documentId,
            Long ownerId) {

        CourseDocument document =
                requireDocument(documentId);

        requireOwner(document.getCourseId(), ownerId);

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

        // 使用数据库条件更新抢占删除任务
        int updated = documentMapper.update(
                null,
                new LambdaUpdateWrapper<CourseDocument>()
                        .eq(CourseDocument::getId, documentId)
                        .eq(
                                CourseDocument::getStatus,
                                document.getStatus()
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

        cleanup(documentId);
    }

    // 重新处理失败的 PDF
    public void retryIndex(
            Long documentId,
            Long ownerId) {

        CourseDocument doc = requireDocument(documentId);

        requireOwner(doc.getCourseId(), ownerId);

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
    public synchronized void resumeDelete(Long documentId) {

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

        } else if (!"DELETING".equals(
                doc.getStatus())) {

            return;
        }

        try {
            cleanup(documentId);
        } catch (BusinessException ex) {
            log.warn(
                    "Document cleanup recovery failed: {}",
                    documentId
            );
        }
    }

    // 实际清理流程
    private void cleanup(Long documentId) {

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

            // 第二步：删除磁盘上的 PDF
            Path filePath = safeFilePath(
                    doc.getStorageName());

            Files.deleteIfExists(filePath);

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

    private void requireOwner(
            Long courseId,
            Long ownerId) {

        Course course =
                courseMapper.selectById(courseId);

        if (course == null
                || !ownerId.equals(
                        course.getOwnerId())) {

            throw new BusinessException(
                    403,
                    "只能管理自己负责课程的文件"
            );
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
