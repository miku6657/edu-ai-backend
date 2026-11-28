package com.edu.ai.document.dto;

import java.time.LocalDate;

public record DocumentExpiryVO(

        String documentId,

        String courseId,

        String documentName,

        LocalDate validUntil,

        String updateResponsibleId,

        Integer expiryRemindDays,

        String expiryStatus,

        Long remainingDays

) {}
