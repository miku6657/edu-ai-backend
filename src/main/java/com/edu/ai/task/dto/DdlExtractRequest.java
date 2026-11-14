package com.edu.ai.task.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.Data;

@Data
public class DdlExtractRequest {

    /**
     * 前端原始通知文本。
     * JSON: raw_text
     */
    @JsonProperty("raw_text")
    @NotBlank(message = "待提取文本不能为空")
    @Size(max = 10000, message = "文本不能超过10000个字符")
    private String sourceText;

    /**
     * 请求唯一标识。
     * JSON: client_request_id
     */
    @JsonProperty("client_request_id")
    @NotBlank(message = "请求编号不能为空")
    @Size(max = 100, message = "请求编号不能超过100个字符")
    private String clientRequestId;

    /**
     * 是否强制创建新任务。
     * JSON: force_create
     */
    @JsonProperty("force_create")
    private Boolean forceCreate = false;

    /**
     * 所属课程 ID。
     * JSON: course_id
     */
    @JsonProperty("course_id")
    private Long courseId;

    /**
     * 前端提供的参考时间。
     * JSON: reference_time
     *
     * 使用 ISO-8601 字符串，避免丢失时区偏移。
     */
    @JsonProperty("reference_time")
    private String referenceTime;
}
