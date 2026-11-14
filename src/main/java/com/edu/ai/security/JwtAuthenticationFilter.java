package com.edu.ai.security;

import com.edu.ai.system.entity.User;
import com.edu.ai.system.mapper.UserMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication
        .UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority
        .SimpleGrantedAuthority;
import org.springframework.security.core.context
        .SecurityContextHolder;
import org.springframework.security.web.authentication
        .WebAuthenticationDetailsSource;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Set;

@Component
public class JwtAuthenticationFilter
        extends OncePerRequestFilter {

    private static final Set<String> VALID_ROLES =
            Set.of("ADMIN", "USER");

    private final JwtTokenProvider jwtTokenProvider;
    private final UserMapper userMapper;

    public JwtAuthenticationFilter(
            JwtTokenProvider jwtTokenProvider,
            UserMapper userMapper) {

        this.jwtTokenProvider = jwtTokenProvider;
        this.userMapper = userMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        if (header == null
                || !header.startsWith("Bearer ")) {

            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(7).trim();

        try {
            if (token.isBlank()
                    || !jwtTokenProvider.validateToken(token)) {

                unauthorized(response, "Token无效或已过期");
                return;
            }

            Long userId = jwtTokenProvider.getUserId(token);

            // 每次请求重新查询数据库
            User user = userMapper.selectById(userId);

            if (user == null) {
                unauthorized(response, "用户不存在");
                return;
            }

            // 检查用户是否已禁用
            if (!Integer.valueOf(1).equals(user.getStatus())) {
                unauthorized(response, "账号已被禁用");
                return;
            }

            String role = user.getRole();

            if (role == null || !VALID_ROLES.contains(role)) {
                unauthorized(response, "用户角色无效");
                return;
            }

            // 权限以数据库中的当前角色为准
            List<SimpleGrantedAuthority> authorities =
                    List.of(
                            new SimpleGrantedAuthority(
                                    "ROLE_" + role)
                    );

            // 保持 Long 类型 principal，
            // 兼容已有 Controller 的身份获取方式
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            user.getId(),
                            null,
                            authorities
                    );

            authentication.setDetails(
                    new WebAuthenticationDetailsSource()
                            .buildDetails(request)
            );

            SecurityContextHolder.getContext()
                    .setAuthentication(authentication);

            filterChain.doFilter(request, response);

        } catch (io.jsonwebtoken.JwtException
                 | IllegalArgumentException e) {

            SecurityContextHolder.clearContext();
            unauthorized(response, "Token认证失败");

        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void unauthorized(
            HttpServletResponse response,
            String message) throws IOException {

        if (response.isCommitted()) {
            return;
        }

        response.setStatus(401);
        response.setCharacterEncoding("UTF-8");
        response.setContentType(
                "application/json;charset=UTF-8");

        String body = """
                {
                  "code": 401,
                  "message": "%s",
                  "data": null
                }
                """.formatted(message);

        response.getWriter().write(body);
    }
}
