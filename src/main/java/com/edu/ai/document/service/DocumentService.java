package com.edu.ai.document.service;

import com.edu.ai.document.dto.DocumentVO;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface DocumentService {

    // 上传课件
    DocumentVO upload(
            Long courseId,
            Long userId,
            MultipartFile file
    );

    // 查看课程课件
    List<DocumentVO> listByCourse(
            Long courseId,
            Long userId
    );

    // 下载课件
    DownloadFile download(
            Long documentId,
            Long userId
    );

    record DownloadFile(
            Resource resource,
            String filename
    ) {}
}
