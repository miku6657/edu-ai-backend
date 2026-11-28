package com.edu.ai.document.service;

import com.edu.ai.common.BusinessException;
import com.edu.ai.course.service.CoursePermissionService;
import com.edu.ai.document.dto.DocumentQualityVO;
import com.edu.ai.document.entity.CourseDocument;
import com.edu.ai.document.mapper.CourseDocumentMapper;

import org.springframework.stereotype.Service;

@Service
public class DocumentQualityScanService {

    private final CoursePermissionService permissions;

    private final CourseDocumentMapper documentMapper;

    private final PdfQualityInspectionClient inspectionClient;

    private final DocumentQualityService qualityService;

    public DocumentQualityScanService(
            CoursePermissionService permissions,
            CourseDocumentMapper documentMapper,
            PdfQualityInspectionClient inspectionClient,
            DocumentQualityService qualityService) {

        this.permissions = permissions;
        this.documentMapper = documentMapper;
        this.inspectionClient = inspectionClient;
        this.qualityService = qualityService;
    }

    /**
     * 执行资料自动质量检查。
     */
    public DocumentQualityVO.Detail scan(
            Long courseId,
            Long documentId,
            Long userId) {

        permissions.requireManage(
                courseId,
                userId
        );

        CourseDocument document =
                documentMapper.selectById(documentId);

        if (document == null
                || !courseId.equals(document.getCourseId())) {

            throw new BusinessException(
                    404,
                    "课程资料不存在"
            );
        }

        if (!"READY".equals(document.getStatus())
                || !"CURRENT".equals(document.getVersionStatus())
                || !"APPROVED".equals(document.getReviewStatus())) {

            throw new BusinessException(
                    409,
                    "只有当前正式发布的资料才能进行自动质量检查"
            );
        }

        if (document.getStorageName() == null
                || document.getStorageName().isBlank()) {

            throw new BusinessException(
                    409,
                    "资料文件路径不存在"
            );
        }

        // 第一步：调用Python检查PDF物理页面。
        PdfQualityInspectionClient.InspectionResult inspection =
                inspectionClient.inspect(
                        document.getStorageName()
                );

        // 第二步：将检查结果持久化到现有质量整改体系。
        return qualityService.createAutomaticScanTask(
                courseId,
                documentId,
                userId,
                inspection
        );
    }
}
