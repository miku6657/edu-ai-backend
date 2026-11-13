package com.edu.ai.document.service;

import com.edu.ai.document.dto.DocumentVO;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface DocumentService {

    DocumentVO upload(
            Long courseId,
            Long ownerId,
            MultipartFile file
    );

    List<DocumentVO> listByCourse(
            Long courseId,
            Long userId,
            String role
    );

    DownloadFile download(
            Long documentId,
            Long userId,
            String role
    );

    record DownloadFile(
            Resource resource,
            String filename
    ) {}
}
