package com.edu.ai.system.controller;

import com.edu.ai.common.BusinessException;
import com.edu.ai.common.Result;

import com.edu.ai.security.JwtTokenProvider;
import com.edu.ai.security.LoginUser;

import com.edu.ai.system.dto.RegisterRequest;
import com.edu.ai.system.entity.User;
import com.edu.ai.system.service.AuthService;
import com.edu.ai.system.service.UserService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserService userService;
    private final AuthService authService;

    public AuthController(
            AuthenticationManager authenticationManager,
            JwtTokenProvider jwtTokenProvider,
            UserService userService,
            AuthService authService) {

        this.authenticationManager = authenticationManager;
        this.jwtTokenProvider = jwtTokenProvider;
        this.userService = userService;
        this.authService = authService;
    }

    /**
     * 用户注册
     */
    @PostMapping("/register")
    public Result<Map<String, Object>> register(
            @Valid @RequestBody RegisterRequest request) {

        return Result.success(
                authService.register(request)
        );
    }

    /**
     * 用户登录
     */
    @PostMapping("/login")
    public Result<Map<String, Object>> login(
            @Valid @RequestBody LoginRequest request) {

        Authentication authentication =
                authenticationManager.authenticate(
                        new UsernamePasswordAuthenticationToken(
                                request.username(),
                                request.password()
                        )
                );

        LoginUser loginUser =
                (LoginUser) authentication.getPrincipal();

        String token = jwtTokenProvider.generateToken(
                loginUser.getUserId(),
                loginUser.getRole()
        );

        return Result.success(Map.of(
                "token", token,
                "tokenType", "Bearer",
                "userId", String.valueOf(loginUser.getUserId()),
                "username", loginUser.getUsername(),
                "role", loginUser.getRole()
        ));
    }

    /**
     * 获取当前登录用户信息
     */
    @GetMapping("/me")
    public Result<Map<String, Object>> currentUser(
            Authentication authentication) {

        if (authentication == null
                || !authentication.isAuthenticated()) {

            throw new BusinessException(401, "请先登录");
        }

        Long userId;

        if (authentication.getPrincipal() instanceof LoginUser user) {
            userId = user.getUserId();

        } else if (authentication.getPrincipal() instanceof Long id) {
            userId = id;

        } else {
            throw new BusinessException(401, "登录状态无效");
        }

        User user = userService.getUserById(userId);

        if (user == null
                || !Integer.valueOf(1).equals(user.getStatus())) {

            throw new BusinessException(
                    401, "用户不存在或已被禁用");
        }

        return Result.success(Map.of(
                "id", String.valueOf(user.getId()),
                "username", user.getUsername(),
                "nickname", user.getNickname() == null
                        ? "" : user.getNickname(),
                "role", user.getRole()
        ));
    }

    /**
     * 登录请求参数
     */
    public record LoginRequest(

            @NotBlank(message = "用户名不能为空")
            String username,

            @NotBlank(message = "密码不能为空")
            String password

    ) {
    }
}
