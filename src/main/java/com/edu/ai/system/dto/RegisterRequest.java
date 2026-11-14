package com.edu.ai.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(

        @NotBlank(message = "用户名不能为空")
        @Size(min = 3, max = 64, message = "用户名长度必须为3-64位")
        @Pattern(
                regexp = "^[a-zA-Z0-9_-]{3,64}$",
                message = "用户名只能包含字母、数字、下划线和连字符"
        )
        String username,

        @NotBlank(message = "密码不能为空")
        @Size(min = 8, max = 64, message = "密码长度必须为8-64位")
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,64}$",
                message = "密码必须同时包含字母和数字"
        )
        String password

) {
}
