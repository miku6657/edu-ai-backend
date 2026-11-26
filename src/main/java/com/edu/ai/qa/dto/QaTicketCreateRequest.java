package com.edu.ai.qa.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record QaTicketCreateRequest(

        @NotNull(message = "问题消息ID不能为空")
        @Positive(message = "问题消息ID必须大于0")
        Long questionMessageId

) {
}
