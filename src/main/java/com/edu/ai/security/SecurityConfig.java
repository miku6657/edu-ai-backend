package com.edu.ai.security;

import tools.jackson.databind.ObjectMapper;

import com.edu.ai.common.Result;

import jakarta.servlet.DispatcherType;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.http.HttpMethod;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;

import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;

import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * 密码加密器
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 认证管理器
     */
    @Bean
    public AuthenticationManager authenticationManager(
            UserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder) {

        DaoAuthenticationProvider provider =
                new DaoAuthenticationProvider(userDetailsService);

        provider.setPasswordEncoder(passwordEncoder);

        return new ProviderManager(provider);
    }

    /**
     * Spring Security 核心配置
     */
    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtFilter,
            @Qualifier("corsConfigurationSource")
            CorsConfigurationSource corsSource,
            ObjectMapper objectMapper) throws Exception {

        http
                // JWT 无状态认证，不使用 CSRF 表单会话
                .csrf(csrf -> csrf.disable())

                // CORS
                .cors(cors ->
                        cors.configurationSource(corsSource))

                // 无状态 Session
                .sessionManagement(session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS))

                // URL 权限
                .authorizeHttpRequests(auth -> auth

                        // 放行 Servlet 错误分发
                        // 防止 /error 二次鉴权导致响应已提交异常
                        .dispatcherTypeMatchers(
                                DispatcherType.ERROR
                        ).permitAll()

                        // 放行跨域预检
                        .requestMatchers(
                                HttpMethod.OPTIONS, "/**"
                        ).permitAll()

                        // 放行 Spring Boot 错误处理路径
                        .requestMatchers("/error")
                        .permitAll()

                        // 登录和注册公开
                        .requestMatchers(
                                "/api/auth/login",
                                "/api/auth/register"
                        ).permitAll()

                        // 管理员接口
                        .requestMatchers("/api/admin/**")
                        .hasRole("ADMIN")

                        // 其余接口必须登录
                        .anyRequest()
                        .authenticated()
                )

                // 统一认证异常
                .exceptionHandling(exception -> exception

                        .authenticationEntryPoint(
                                (request, response, error) -> {

                                    if (response.isCommitted()) {
                                        return;
                                    }

                                    response.setStatus(401);
                                    response.setCharacterEncoding("UTF-8");
                                    response.setContentType(
                                            "application/json;charset=UTF-8");

                                    response.getWriter().write(
                                            objectMapper.writeValueAsString(
                                                    Result.error(
                                                            401,
                                                            "请先登录或重新登录"
                                                    )
                                            )
                                    );
                                }
                        )

                        .accessDeniedHandler(
                                (request, response, error) -> {

                                    if (response.isCommitted()) {
                                        return;
                                    }

                                    response.setStatus(403);
                                    response.setCharacterEncoding("UTF-8");
                                    response.setContentType(
                                            "application/json;charset=UTF-8");

                                    response.getWriter().write(
                                            objectMapper.writeValueAsString(
                                                    Result.error(
                                                            403,
                                                            "没有访问权限"
                                                    )
                                            )
                                    );
                                }
                        )
                )

                // JWT 过滤器
                .addFilterBefore(
                        jwtFilter,
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }

    /**
     * 禁止 JWT Filter 被 Servlet 容器重复注册
     */
    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter>
    jwtFilterRegistration(JwtAuthenticationFilter filter) {

        FilterRegistrationBean<JwtAuthenticationFilter> registration =
                new FilterRegistrationBean<>(filter);

        registration.setEnabled(false);

        return registration;
    }
}
