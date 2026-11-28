package com.edu.ai.hotquestion.dto;

import java.time.LocalDateTime;

public record CourseHotQuestionPublicVO(

        String id,

        String knowledgePointTitle,

        String questionText,

        String answerText,

        String category,

        LocalDateTime publishedAt

) {}
