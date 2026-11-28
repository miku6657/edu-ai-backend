package com.edu.ai.hotquestion.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CourseHotQuestionReviewRequest(

        @NotNull
        Boolean approved,

        @Size(max = 1000)
        String comment

) {}
