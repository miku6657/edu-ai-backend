package com.edu.ai.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import com.edu.ai.common.BusinessException;

import com.edu.ai.security.JwtTokenProvider;

import com.edu.ai.system.dto.RegisterRequest;
import com.edu.ai.system.entity.User;
import com.edu.ai.system.mapper.UserMapper;
import com.edu.ai.system.service.AuthService;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;

@Service
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthServiceImpl(
            UserMapper userMapper,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider) {

        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> register(RegisterRequest request) {

        if (request == null) {
            throw new BusinessException(400, "注册参数不能为空");
        }

        String username = request.username().trim();
        String password = request.password();

        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BusinessException(
                    400, "密码不能超过72个UTF-8字节");
        }

        // 检查用户名是否已经存在
        Long count = userMapper.selectCount(
                new LambdaQueryWrapper<User>()
                        .eq(User::getUsername, username)
        );

        if (count != null && count > 0) {
            throw new BusinessException(409, "用户名已存在");
        }

        LocalDateTime now = LocalDateTime.now();

        User user = new User();

        user.setUsername(username);

        // 密码必须经过 BCrypt 加密
        user.setPassword(passwordEncoder.encode(password));

        // 前端目前没有昵称字段，默认使用用户名
        user.setNickname(username);

        // 普通注册用户固定为 USER
        user.setRole("USER");

        // 1：正常
        user.setStatus(1);

        user.setCreateTime(now);
        user.setUpdateTime(now);

        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            // 并发注册时由数据库唯一索引兜底
            throw new BusinessException(409, "用户名已存在");
        }

        // 注册成功后立即签发 JWT
        String token = jwtTokenProvider.generateToken(
                user.getId(),
                user.getRole()
        );

        return Map.of(
                "token", token,
                "tokenType", "Bearer",
                "userId", String.valueOf(user.getId()),
                "username", user.getUsername(),
                "role", user.getRole()
        );
    }
}
