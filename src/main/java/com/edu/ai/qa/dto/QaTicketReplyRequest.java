package com.edu.ai.qa.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record QaTicketReplyRequest(

        @NotBlank(message = "答复内容不能为空")
        @Size(
                max = 5000,
                message = "答复内容不能超过5000个字符"
        )
        String reply

) {
}
