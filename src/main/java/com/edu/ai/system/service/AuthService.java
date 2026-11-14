package com.edu.ai.system.service;

import com.edu.ai.system.dto.RegisterRequest;

import java.util.Map;

public interface AuthService {

    /**
     * 注册普通用户，并签发 JWT
     */
    Map<String, Object> register(RegisterRequest request);
}
