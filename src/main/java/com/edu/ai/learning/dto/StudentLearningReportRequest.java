package com.edu.ai.learning.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * F6-03：生成阶段学习报告的时间范围。
 */
public record StudentLearningReportRequest(

        @NotNull(message = "统计开始日期不能为空")
        LocalDate startDate,

        @NotNull(message = "统计结束日期不能为空")
        LocalDate endDate

) {}
