package com.edu.ai.course.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.Data;

@Data
public class JoinCourseRequest {

    @NotBlank(message = "课程邀请码不能为空")
    @Size(min = 6, max = 12, message = "课程邀请码格式不正确")
    private String inviteCode;
}
