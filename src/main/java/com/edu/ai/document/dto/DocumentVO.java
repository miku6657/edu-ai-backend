package com.edu.ai.document.dto;

import com.edu.ai.document.entity.CourseDocument;
import lombok.Data;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

import java.time.LocalDateTime;

@Data
public class DocumentVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long courseId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long uploaderId;

    private String originalName;

    private Long fileSize;

    private String status;

    private String errorMessage;

    private LocalDateTime createTime;

    public static DocumentVO from(CourseDocument doc) {

        DocumentVO vo = new DocumentVO();

        vo.setId(doc.getId());
        vo.setCourseId(doc.getCourseId());
        vo.setUploaderId(doc.getUploaderId());
        vo.setOriginalName(doc.getOriginalName());
        vo.setFileSize(doc.getFileSize());
        vo.setStatus(doc.getStatus());
        vo.setErrorMessage(doc.getErrorMessage());
        vo.setCreateTime(doc.getCreateTime());

        return vo;
    }
}
