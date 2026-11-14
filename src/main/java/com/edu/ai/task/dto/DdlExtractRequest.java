package com.edu.ai.task.dto;

import tools.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.Data;

@Data
public class DdlExtractRequest {

    @NotBlank(message = "待提取文本不能为空")
    @Size(max = 10000, message = "文本不能超过10000个字符")
    private String sourceText;

    @JsonProperty("course_id")
    private Long courseId;

    @JsonProperty("course_name")
    private String courseName;

    @JsonProperty("client_request_id")
    @NotBlank(message = "请求编号不能为空")
    private String clientRequestId;

    @JsonProperty("user_timezone")
    private String userTimezone;
}
