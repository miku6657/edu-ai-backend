package com.edu.ai.hotquestion.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CourseHotQuestionCreateRequest(

        @NotBlank
        @Size(max = 2000)
        String sourceQuestionText,

        Long knowledgePointId,

        @NotBlank
        @Size(max = 500)
        String questionText,

        @NotBlank
        @Size(max = 5000)
        String answerText,

        @Size(max = 50)
        String category

) {}
