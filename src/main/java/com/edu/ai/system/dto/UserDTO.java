
package com.edu.ai.system.dto;

import lombok.Data;

@Data
public class UserDTO {

    // 用户 ID（修改时使用）
    private Long id;

    // 用户名
    private String username;

    // 密码（新增用户时使用）
    private String password;

    // 昵称
    private String nickname;

    // 角色：ADMIN、USER
    private String role;

    // 用户状态：1 正常，0 禁用
    private Integer status;
}

