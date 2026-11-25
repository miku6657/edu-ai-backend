
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
    private final Path storageRoot;

    public DocumentServiceImpl(
            CourseDocumentMapper documentMapper,
            CoursePermissionService coursePermissionService,
            @Value("${app.storage.pdf-dir}") String storageDir) {

        this.documentMapper = documentMapper;
        this.coursePermissionService = coursePermissionService;

        this.storageRoot = Paths.get(storageDir)
                .toAbsolutePath()
                .normalize();
    }

    /**
     * 上传 PDF
     * 所有登录用户均可上传
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocumentVO upload(
            Long courseId,
            Long userId,
            MultipartFile file,
            String resourceType,
            String sourceInfo) {

        Course course = coursePermissionService.requireManage(
                courseId, userId
        );

        if (!Integer.valueOf(1).equals(course.getStatus())) {
            throw new BusinessException(400, "课程已停用");
        }

        validatePdf(file);

        String originalName =
                cleanFilename(file.getOriginalFilename());

        // 教师明确选择类型时使用指定类型；
        // AUTO 时按文件名规则识别。
        String resolvedType =
                DocumentResourceType.resolve(
                        resourceType,
                        originalName
                );

        // 不允许来源字段无限长。
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

        Path target = storageRoot.resolve(storageName).normalize();

        if (!target.startsWith(storageRoot)) {
            throw new BusinessException(400, "非法文件路径");
        }

        try {
            Files.createDirectories(storageRoot);

            // 保存 PDF 文件
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, target);
            }

            CourseDocument doc = new CourseDocument();

            doc.setCourseId(courseId);
            doc.setUploaderId(userId);
            doc.setOriginalName(originalName);
            doc.setStorageName(storageName);
            doc.setFileSize(file.getSize());
            doc.setContentType("application/pdf");

            // 新增教学资源元数据
            doc.setResourceType(resolvedType);
            doc.setSourceInfo(resolvedSource);

            doc.setStatus("UPLOADED");
            doc.setVersionNo(1);
            doc.setVersionStatus("PENDING");
            doc.setCreateTime(LocalDateTime.now());
            doc.setUpdateTime(LocalDateTime.now());

            try {
                documentMapper.insert(doc);

                if (doc.getId() == null) {
                    throw new IllegalStateException(
                            "文档保存后未获得ID"
                    );
                }

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

            } catch (RuntimeException e) {

                try {
                    Files.deleteIfExists(target);
                } catch (IOException cleanupError) {
                    e.addSuppressed(cleanupError);
                }

                throw e;
            }

            return DocumentVO.from(doc);

        } catch (IOException e) {

            try {
                Files.deleteIfExists(target);
            } catch (IOException cleanupError) {
                e.addSuppressed(cleanupError);
            }

            throw new BusinessException(500, "PDF 文件保存失败");
        }
    }

    /**
     * 查询课程资料
     *
     * 所有用户都能查看 READY 文档；
     * 上传者还可以查看自己尚未处理完成的文档。
     */
    @Override
    public List<DocumentVO> listByCourse(
            Long courseId,
            Long userId) {

        coursePermissionService.requireActiveRead(
                courseId, userId
        );

        LambdaQueryWrapper<CourseDocument> query =
                new LambdaQueryWrapper<CourseDocument>()
                        .eq(CourseDocument::getCourseId, courseId)
                        .and(wrapper ->
                                wrapper.eq(
                                                CourseDocument::getStatus,
                                                "READY"
                                        )
                                        .or()
                                        .eq(
                                                CourseDocument::getUploaderId,
                                                userId
                                        )
                        )
                        .notIn(
                                CourseDocument::getStatus,
                                "DELETING",
                                "DELETE_FAILED",
                                "DELETED"
                        )
                        .orderByDesc(CourseDocument::getCreateTime);

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
