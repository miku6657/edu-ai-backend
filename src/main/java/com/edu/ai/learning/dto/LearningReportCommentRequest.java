package com.edu.ai.learning.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 教师学习报告文字批注请求。
 */
public record LearningReportCommentRequest(

        @NotBlank(message = "批注内容不能为空")

        @Size(
                max = 2000,
                message = "批注内容不能超过2000字"
        )

        String content

) {}
