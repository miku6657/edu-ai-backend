package com.edu.ai.document.service;

import com.edu.ai.document.dto.DocumentVO;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface DocumentService {

    /**
     * 旧上传方法保留，兼容已有调用。
     */
    default DocumentVO upload(
            Long courseId,
            Long userId,
            MultipartFile file) {

        return upload(
                courseId,
                userId,
                file,
                "AUTO",
                null
        );
    }

    /**
     * 上传 PDF 并登记资源元数据。
     */
    DocumentVO upload(
            Long courseId,
            Long userId,
            MultipartFile file,
            String resourceType,
            String sourceInfo
    );

    /**
     * 为当前生效的资源上传一个新版本。
     */
    DocumentVO uploadNewVersion(
            Long documentId,
            Long userId,
            MultipartFile file
    );

    /**
     * 合并同一课程下的同类型PDF资源。
     */
    DocumentVO mergeDocuments(
            Long courseId,
            Long userId,
            List<Long> documentIds,
            String filename
    );

    List<DocumentVO> listByCourse(
            Long courseId,
            Long userId
    );

    DownloadFile download(
            Long documentId,
            Long userId
    );

    record DownloadFile(
            Resource resource,
            String filename
    ) {}
}
