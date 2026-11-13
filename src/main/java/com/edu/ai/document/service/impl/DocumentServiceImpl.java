package com.edu.ai.document.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.edu.ai.common.BusinessException;
import com.edu.ai.course.entity.Course;
import com.edu.ai.course.entity.CourseStudent;
import com.edu.ai.course.mapper.CourseMapper;
import com.edu.ai.course.mapper.CourseStudentMapper;
import com.edu.ai.document.dto.DocumentVO;
import com.edu.ai.document.entity.CourseDocument;
import com.edu.ai.document.mapper.CourseDocumentMapper;
import com.edu.ai.document.service.DocumentService;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class DocumentServiceImpl implements DocumentService {

    private static final long MAX_FILE_SIZE = 30L * 1024 * 1024;

    private final CourseDocumentMapper documentMapper;
    private final CourseMapper courseMapper;
    private final CourseStudentMapper courseStudentMapper;
    private final Path storageRoot;

    public DocumentServiceImpl(
            CourseDocumentMapper documentMapper,
            CourseMapper courseMapper,
            CourseStudentMapper courseStudentMapper,
            @Value("${app.storage.pdf-dir}") String storageDir) {

        this.documentMapper = documentMapper;
        this.courseMapper = courseMapper;
        this.courseStudentMapper = courseStudentMapper;

        this.storageRoot = Paths.get(storageDir)
                .toAbsolutePath()
                .normalize();
    }

    // PDF 上传
    @Override
    public DocumentVO upload(
            Long courseId,
            Long ownerId,
            MultipartFile file) {

        Course course = getCourse(courseId);

        // 只有课程负责人能上传
        if (!course.getOwnerId().equals(ownerId)) {
            throw new BusinessException(
                    403, "只能向自己负责的课程上传文件");
        }

        if (!Integer.valueOf(1).equals(course.getStatus())) {
            throw new BusinessException(400, "课程已停用");
        }

        validatePdf(file);

        String originalName =
                cleanFilename(file.getOriginalFilename());

        String storageName = UUID.randomUUID() + ".pdf";
        Path target = storageRoot.resolve(storageName).normalize();

        if (!target.startsWith(storageRoot)) {
            throw new BusinessException(400, "非法文件路径");
        }

        try {
            Files.createDirectories(storageRoot);

            // 先保存文件
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, target);
            }

            CourseDocument doc = new CourseDocument();

            doc.setCourseId(courseId);
            doc.setUploaderId(ownerId);
            doc.setOriginalName(originalName);
            doc.setStorageName(storageName);
            doc.setFileSize(file.getSize());
            doc.setContentType("application/pdf");
            doc.setStatus("UPLOADED");
            doc.setCreateTime(LocalDateTime.now());
            doc.setUpdateTime(LocalDateTime.now());

            // 写入数据库；失败时清理刚保存的文件
            try {
                documentMapper.insert(doc);
            } catch (RuntimeException e) {
                Files.deleteIfExists(target);
                throw e;
            }

            return DocumentVO.from(doc);

        } catch (IOException e) {
            try {
                Files.deleteIfExists(target);
            } catch (IOException cleanupError) {
                e.addSuppressed(cleanupError);
            }

            throw new BusinessException(
                    500, "PDF 文件保存失败");
        }
    }

    // 课程资料列表
    @Override
    public List<DocumentVO> listByCourse(
            Long courseId,
            Long userId,
            String role) {

        validateCourseAccess(courseId, userId, role);

        LambdaQueryWrapper<CourseDocument> query =
                new LambdaQueryWrapper<CourseDocument>()
                        .eq(CourseDocument::getCourseId, courseId)
                        .orderByDesc(
                                CourseDocument::getCreateTime);

        if ("STUDENT".equals(role)) {

            // 学生只能查看已处理成功的资料
            query.eq(CourseDocument::getStatus, "READY");

        } else {

            // 负责人可以查看处理失败或正在删除的记录，
            // 但不再显示已经删除的文档
            query.ne(CourseDocument::getStatus, "DELETED");
        }

        return documentMapper.selectList(query)
                .stream()
                .map(DocumentVO::from)
                .toList();
    }

    // 下载 PDF
    @Override
    public DownloadFile download(
            Long documentId,
            Long userId,
            String role) {

        CourseDocument doc =
                documentMapper.selectById(documentId);

        if (doc == null) {
            throw new BusinessException(404, "资料不存在");
        }

        if ("DELETING".equals(doc.getStatus())
                || "DELETE_FAILED".equals(doc.getStatus())
                || "DELETED".equals(doc.getStatus())) {

            throw new BusinessException(
                    404, "该资料已删除或正在删除");
        }

        validateCourseAccess(
                doc.getCourseId(), userId, role);

        if ("STUDENT".equals(role)
                && !"READY".equals(doc.getStatus())) {
            throw new BusinessException(
                    403, "该资料尚未开放下载");
        }

        String storageName = doc.getStorageName();

        // 文件名必须符合系统生成的 UUID.pdf 格式
        if (storageName == null
                || !storageName.matches(
                        "[0-9a-fA-F-]{36}\\.pdf")) {
            throw new BusinessException(
                    500, "文件存储记录异常");
        }

        Path target = storageRoot
                .resolve(storageName)
                .normalize();

        if (!target.startsWith(storageRoot)) {
            throw new BusinessException(
                    403, "非法文件路径");
        }

        if (!Files.isRegularFile(
                target, LinkOption.NOFOLLOW_LINKS)
                || !Files.isReadable(target)) {

            throw new BusinessException(
                    404, "文件不存在或无法读取");
        }

        return new DownloadFile(
                new FileSystemResource(target),
                doc.getOriginalName()
        );
    }

    // 检查课程访问权限
    private void validateCourseAccess(
            Long courseId,
            Long userId,
            String role) {

        Course course = getCourse(courseId);

        if ("OWNER".equals(role)) {

            if (!course.getOwnerId().equals(userId)) {
                throw new BusinessException(
                        403, "无权访问该课程资料");
            }

            return;
        }

        if ("STUDENT".equals(role)) {

            if (!Integer.valueOf(1).equals(
                    course.getStatus())) {
                throw new BusinessException(
                        403, "课程暂不可访问");
            }

            Long count = courseStudentMapper.selectCount(
                    new LambdaQueryWrapper<CourseStudent>()
                            .eq(CourseStudent::getCourseId,
                                    courseId)
                            .eq(CourseStudent::getStudentId,
                                    userId)
            );

            if (count == 0) {
                throw new BusinessException(
                        403, "请先加入该课程");
            }

            return;
        }

        throw new BusinessException(403, "无权访问资料");
    }

    private Course getCourse(Long courseId) {

        Course course = courseMapper.selectById(courseId);

        if (course == null) {
            throw new BusinessException(404, "课程不存在");
        }

        return course;
    }

    // 验证 PDF 文件大小、扩展名和文件头
    private void validatePdf(MultipartFile file) {

        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "请选择 PDF 文件");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(
                    400, "PDF 文件不能超过 30MB");
        }

        String name = file.getOriginalFilename();

        if (name == null ||
                !name.toLowerCase(java.util.Locale.ROOT)
                        .endsWith(".pdf")) {
            throw new BusinessException(
                    400, "只允许上传 PDF 文件");
        }

        try (InputStream input = file.getInputStream()) {

            byte[] header = input.readNBytes(5);

            String signature = new String(
                    header, StandardCharsets.US_ASCII);

            if (!"%PDF-".equals(signature)) {
                throw new BusinessException(
                        400, "文件内容不是有效的 PDF 格式");
            }

        } catch (IOException e) {
            throw new BusinessException(
                    400, "无法读取上传的 PDF 文件");
        }
    }

    // 只保留文件名称，过滤目录及控制字符
    private String cleanFilename(String filename) {

        String cleaned = filename
                .replace('\\', '/');

        cleaned = cleaned.substring(
                cleaned.lastIndexOf('/') + 1
        );

        cleaned = cleaned.replaceAll(
                "[\\p{Cntrl}]", "_");

        if (cleaned.isBlank()) {
            cleaned = "document.pdf";
        }

        if (cleaned.length() > 255) {
            cleaned = cleaned.substring(0, 251) + ".pdf";
        }

        return cleaned;
    }
}
