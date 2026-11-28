package com.edu.ai.document.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record DocumentExpiryRequest(

        LocalDate validUntil,

        Long updateResponsibleId,

        @NotNull(message = "提前提醒天数不能为空")
        @Min(value = 0, message = "提前提醒天数不能小于0")
        @Max(value = 90, message = "提前提醒天数不能超过90")
        Integer expiryRemindDays

) {}
