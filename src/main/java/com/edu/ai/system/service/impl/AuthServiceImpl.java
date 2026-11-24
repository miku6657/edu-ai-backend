package com.edu.ai.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import com.edu.ai.common.BusinessException;

import com.edu.ai.security.JwtTokenProvider;

import com.edu.ai.system.dto.RegisterRequest;
import com.edu.ai.system.entity.User;
import com.edu.ai.system.enums.UserRole;
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
                    400,
                    "密码不能超过72个UTF-8字节"
            );
        }

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
        user.setPassword(passwordEncoder.encode(password));
        user.setNickname(username);

        // 普通注册只能成为学生
        // 教师和管理员由管理员在后台创建
        user.setRole(UserRole.STUDENT.name());

        user.setStatus(1);
        user.setCreateTime(now);
        user.setUpdateTime(now);

        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(409, "用户名已存在");
        }

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
