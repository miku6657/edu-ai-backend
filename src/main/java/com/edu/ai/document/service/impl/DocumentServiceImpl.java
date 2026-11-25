
package com.edu.ai.document.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.edu.ai.common.BusinessException;
import com.edu.ai.course.entity.Course;
import com.edu.ai.course.service.CoursePermissionService;
import com.edu.ai.document.dto.DocumentVO;
import com.edu.ai.document.entity.CourseDocument;
import com.edu.ai.document.mapper.CourseDocumentMapper;
import com.edu.ai.document.service.DocumentResourceType;
import com.edu.ai.document.service.DocumentService;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class DocumentServiceImpl implements DocumentService {

    private static final long MAX_FILE_SIZE = 30L * 1024 * 1024;

    private final CourseDocumentMapper documentMapper;
    private final CoursePermissionService coursePermissionService;
    private final JdbcTemplate jdbc;
    private final Path storageRoot;

    public DocumentServiceImpl(
            CourseDocumentMapper documentMapper,
            CoursePermissionService coursePermissionService,
            JdbcTemplate jdbc,
            @Value("${app.storage.pdf-dir}") String storageDir) {

        this.documentMapper = documentMapper;
        this.coursePermissionService = coursePermissionService;
        this.jdbc = jdbc;

        this.storageRoot = Paths.get(storageDir)
                .toAbsolutePath()
                .normalize();
    }

    /**
     * 首次上传一份课程资源。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocumentVO upload(
            Long courseId,
            Long userId,
            MultipartFile file,
            String resourceType,
            String sourceInfo) {

        return saveDocument(
                courseId,
                userId,
                file,
                resourceType,
                sourceInfo,
                null,
                1
        );
    }

    /**
     * 普通上传与版本上传共用的文件保存逻辑。
     *
     * resourceId为null：创建新的独立资源。
     * resourceId非null：在已有资源下创建新版本。
     */
    private DocumentVO saveDocument(
            Long courseId,
            Long userId,
            MultipartFile file,
            String resourceType,
            String sourceInfo,
            Long resourceId,
            int versionNo) {

        Course course = coursePermissionService.requireManage(
                courseId, userId
        );

        if (!Integer.valueOf(1).equals(course.getStatus())) {
            throw new BusinessException(400, "课程已停用");
        }

        validatePdf(file);

        String originalName =
                cleanFilename(file.getOriginalFilename());

        String resolvedType =
                DocumentResourceType.resolve(
                        resourceType,
                        originalName
                );

        String resolvedSource =
                sourceInfo == null
                        ? null
                        : sourceInfo.trim();

        if (resolvedSource != null
                && resolvedSource.length() > 255) {
            throw new BusinessException(
                    400, "资源来源不能超过255个字符"
            );
        }

        if (resolvedSource != null
                && resolvedSource.isBlank()) {
            resolvedSource = null;
        }

        String storageName = UUID.randomUUID() + ".pdf";

        Path target = storageRoot
                .resolve(storageName)
                .normalize();

        if (!target.startsWith(storageRoot)) {
            throw new BusinessException(
                    400, "非法文件路径"
            );
        }

        try {
            Files.createDirectories(storageRoot);

            try (InputStream input = file.getInputStream()) {
                Files.copy(input, target);
            }

            // 文件已写入，但数据库事务尚未提交。
            // 如果后续插入、复制分类或事务提交失败，
            // 回滚时删除本次新上传的文件。
            registerRollbackCleanup(target);

            CourseDocument doc = new CourseDocument();

            doc.setCourseId(courseId);
            doc.setUploaderId(userId);

            doc.setOriginalName(originalName);
            doc.setStorageName(storageName);
            doc.setFileSize(file.getSize());
            doc.setContentType("application/pdf");

            doc.setResourceType(resolvedType);
            doc.setSourceInfo(resolvedSource);

            doc.setResourceId(resourceId);
            doc.setVersionNo(versionNo);
            doc.setVersionStatus("PENDING");

            doc.setStatus("UPLOADED");

            doc.setCreateTime(LocalDateTime.now());
            doc.setUpdateTime(LocalDateTime.now());

            documentMapper.insert(doc);

            if (doc.getId() == null) {
                throw new IllegalStateException(
                        "文档保存后未获得ID"
                );
            }

            // 首次上传时，使用文档自己的ID作为资源ID。
            if (resourceId == null) {

                int updated = documentMapper.update(
                        null,
                        new LambdaUpdateWrapper<CourseDocument>()
                                .eq(
                                        CourseDocument::getId,
                                        doc.getId()
                                )
                                .set(
                                        CourseDocument::getResourceId,
                                        doc.getId()
                                )
                );

                if (updated != 1) {
                    throw new IllegalStateException(
                            "初始化资源ID失败"
                    );
                }

                doc.setResourceId(doc.getId());
            }

            return DocumentVO.from(doc);

        } catch (IOException e) {

            try {
                Files.deleteIfExists(target);
            } catch (IOException cleanupError) {
                e.addSuppressed(cleanupError);
            }

            throw new BusinessException(
                    500, "PDF 文件保存失败"
            );

        } catch (RuntimeException e) {

            try {
                Files.deleteIfExists(target);
            } catch (IOException cleanupError) {
                e.addSuppressed(cleanupError);
            }

            throw e;
        }
    }

    /**
     * 注册事务回滚文件清理。
     */
    private void registerRollbackCleanup(Path target) {

        if (!TransactionSynchronizationManager
                .isSynchronizationActive()) {

            throw new IllegalStateException(
                    "文档保存必须在事务中执行"
            );
        }

        TransactionSynchronizationManager
                .registerSynchronization(
                        new TransactionSynchronization() {

                            @Override
                            public void afterCompletion(int status) {

                                if (status != STATUS_ROLLED_BACK) {
                                    return;
                                }

                                try {
                                    Files.deleteIfExists(target);
                                } catch (IOException e) {

                                    org.slf4j.LoggerFactory
                                            .getLogger(
                                                    DocumentServiceImpl.class
                                            )
                                            .error(
                                                    "事务回滚后清理PDF失败: {}",
                                                    target,
                                                    e
                                            );
                                }
                            }
                        }
                );
    }

    /**
     * 上传已有资源的新版本。
     *
     * 新版本索引完成之前，原版本继续生效。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocumentVO uploadNewVersion(
            Long documentId,
            Long userId,
            MultipartFile file) {

        CourseDocument source =
                documentMapper.selectById(documentId);

        if (source == null) {
            throw new BusinessException(
                    404, "原始资源不存在"
            );
        }

        coursePermissionService.requireManage(
                source.getCourseId(),
                userId
        );

        Long courseId = source.getCourseId();
        Long resourceId = source.getResourceId();

        if (resourceId == null) {
            throw new BusinessException(
                    409, "资源尚未完成版本初始化"
            );
        }

        // 与DocumentVersionService使用相同的根记录锁。
        List<Long> roots = jdbc.queryForList(
                """
                SELECT id
                FROM course_document
                WHERE id = ?
                  AND course_id = ?
                FOR UPDATE
                """,
                Long.class,
                resourceId,
                courseId
        );

        if (roots.isEmpty()) {
            throw new BusinessException(
                    409, "资源根记录不存在"
            );
        }

        // 加锁后重新读取当前资源，防止替换已经过期的版本。
        List<Long> currentIds = jdbc.queryForList(
                """
                SELECT id
                FROM course_document
                WHERE resource_id = ?
                  AND course_id = ?
                  AND version_status = 'CURRENT'
                  AND status = 'READY'
                FOR UPDATE
                """,
                Long.class,
                resourceId,
                courseId
        );

        if (currentIds.size() != 1
                || !documentId.equals(currentIds.get(0))) {

            throw new BusinessException(
                    409, "资源当前版本已经变化，请刷新后重试"
            );
        }

        // 同一资源最多允许一个尚未处理完成的新版本。
        // FAILED版本需要先重试或删除。
        List<Long> pendingIds = jdbc.queryForList(
                """
                SELECT id
                FROM course_document
                WHERE resource_id = ?
                  AND course_id = ?
                  AND version_status = 'PENDING'
                  AND status <> 'DELETED'
                FOR UPDATE
                """,
                Long.class,
                resourceId,
                courseId
        );

        if (!pendingIds.isEmpty()) {
            throw new BusinessException(
                    409,
                    "已有待处理的新版本，请先完成处理或删除失败版本"
            );
        }

        // 包括已经删除的版本，保证版本号永不重复。
        List<Integer> versions = jdbc.queryForList(
                """
                SELECT version_no
                FROM course_document
                WHERE resource_id = ?
                  AND course_id = ?
                ORDER BY version_no DESC
                LIMIT 1
                FOR UPDATE
                """,
                Integer.class,
                resourceId,
                courseId
        );

        int nextVersion = versions.isEmpty()
                ? 1
                : Math.addExact(versions.get(0), 1);

        // 继承原资源的类型和来源。
        DocumentVO created = saveDocument(
                courseId,
                userId,
                file,
                source.getResourceType(),
                source.getSourceInfo(),
                resourceId,
                nextVersion
        );

        Long newDocumentId = created.getId();

        // 继承原有章节关联。
        jdbc.update(
                """
                INSERT INTO course_document_chapter
                    (document_id, chapter_id)
                SELECT ?, chapter_id
                FROM course_document_chapter
                WHERE document_id = ?
                """,
                newDocumentId,
                documentId
        );

        // 继承原有知识点关联。
        jdbc.update(
                """
                INSERT INTO course_document_knowledge_point
                    (document_id, point_id)
                SELECT ?, point_id
                FROM course_document_knowledge_point
                WHERE document_id = ?
                """,
                newDocumentId,
                documentId
        );

        return created;
    }

    /**
     * 查询课程资料。
     */
    @Override
    public List<DocumentVO> listByCourse(
            Long courseId,
            Long userId) {

        coursePermissionService.requireActiveRead(
                courseId, userId
        );

        boolean canManage =
                coursePermissionService.canManage(
                        courseId,
                        userId
                );

        LambdaQueryWrapper<CourseDocument> query =
                new LambdaQueryWrapper<CourseDocument>()
                        .eq(
                                CourseDocument::getCourseId,
                                courseId
                        )
                        .and(wrapper -> {

                            // 所有课程成员查看当前生效版本。
                            wrapper.nested(current ->
                                    current.eq(
                                            CourseDocument::getVersionStatus,
                                            "CURRENT"
                                    ).eq(
                                            CourseDocument::getStatus,
                                            "READY"
                                    )
                            );

                            // 教师和管理员还可以查看待处理版本，
                            // 以便处理失败、重试或删除。
                            if (canManage) {
                                wrapper.or().eq(
                                        CourseDocument::getVersionStatus,
                                        "PENDING"
                                );
                            }
                        })
                        .notIn(
                                CourseDocument::getStatus,
                                "DELETING",
                                "DELETE_FAILED",
                                "DELETED"
                        )
                        .orderByDesc(
                                CourseDocument::getCreateTime
                        );

        return documentMapper.selectList(query)
                .stream()
                .map(DocumentVO::from)
                .toList();
    }

    /**
     * 下载 PDF
     *
     * READY 文档允许所有登录用户下载；
     * 未处理完成的文档只有上传者可以下载。
     */
    @Override
    public DownloadFile download(
            Long documentId,
            Long userId) {

        CourseDocument doc =
                documentMapper.selectById(documentId);

        if (doc == null) {
            throw new BusinessException(404, "资料不存在");
        }

        if ("DELETING".equals(doc.getStatus())
                || "DELETE_FAILED".equals(doc.getStatus())
                || "DELETED".equals(doc.getStatus())) {

            throw new BusinessException(404, "该资料已删除或正在删除");
        }

        coursePermissionService.requireActiveRead(
                doc.getCourseId(), userId
        );

        if (!"READY".equals(doc.getStatus())
                && !coursePermissionService.canManage(
                        doc.getCourseId(), userId
                )) {
            throw new BusinessException(
                    403,
                    "该资料尚未完成处理"
            );
        }

        String storageName = doc.getStorageName();

        // 检查系统生成的文件名
        if (storageName == null
                || !storageName.matches("[0-9a-fA-F-]{36}\\.pdf")) {

            throw new BusinessException(500, "文件存储记录异常");
        }

        Path target = storageRoot.resolve(storageName).normalize();

        if (!target.startsWith(storageRoot)) {
            throw new BusinessException(403, "非法文件路径");
        }

        if (!Files.isRegularFile(
                target,
                LinkOption.NOFOLLOW_LINKS)
                || !Files.isReadable(target)) {

            throw new BusinessException(404, "文件不存在或无法读取");
        }

        return new DownloadFile(
                new FileSystemResource(target),
                doc.getOriginalName()
        );
    }

    /**
     * 验证 PDF 文件大小、扩展名和文件头
     */
    private void validatePdf(MultipartFile file) {

        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "请选择 PDF 文件");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(400, "PDF 文件不能超过 30MB");
        }

        String name = file.getOriginalFilename();

        if (name == null ||
                !name.toLowerCase(java.util.Locale.ROOT)
                        .endsWith(".pdf")) {

            throw new BusinessException(400, "只允许上传 PDF 文件");
        }

        try (InputStream input = file.getInputStream()) {

            byte[] header = input.readNBytes(5);

            String signature = new String(
                    header,
                    StandardCharsets.US_ASCII
            );

            if (!"%PDF-".equals(signature)) {
                throw new BusinessException(
                        400,
                        "文件内容不是有效的 PDF 格式"
                );
            }

        } catch (IOException e) {
            throw new BusinessException(400, "无法读取上传的 PDF 文件");
        }
    }

    /**
     * 清理文件名称
     */
    private String cleanFilename(String filename) {

        String cleaned = filename.replace('\\', '/');

        cleaned = cleaned.substring(
                cleaned.lastIndexOf('/') + 1
        );

        cleaned = cleaned.replaceAll(
                "[\\p{Cntrl}]",
                "_"
        );

        if (cleaned.isBlank()) {
            cleaned = "document.pdf";
        }

        if (cleaned.length() > 255) {
            cleaned = cleaned.substring(0, 251) + ".pdf";
        }

        return cleaned;
    }
}
