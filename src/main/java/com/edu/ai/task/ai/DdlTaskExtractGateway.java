package com.edu.ai.task.ai;

import com.edu.ai.task.dto.DdlTaskExtractedData;

import java.time.LocalDateTime;

public interface DdlTaskExtractGateway {

    DdlTaskExtractedData extract(
            String sourceText,
            String courseName,
            String timezone,
            LocalDateTime currentTime
    );
}
