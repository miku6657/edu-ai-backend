package com.edu.ai.system.dto;

import lombok.Data;

@Data
public class UserDTO {

    private Long id;

    private String username;

    private String password;

    private String nickname;

    /**
     * 全局角色：
     * ADMIN、TEACHER、STUDENT
     */
    private String role;

    /**
     * 用户状态：
     * 1 正常，0 禁用
     */
    private Integer status;
}
