
package com.edu.ai.security;

import tools.jackson.databind.ObjectMapper;

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

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(
            UserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder) {

        DaoAuthenticationProvider provider =
                new DaoAuthenticationProvider(userDetailsService);

        provider.setPasswordEncoder(passwordEncoder);

        return new ProviderManager(provider);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtFilter,
            @Qualifier("corsConfigurationSource")
            CorsConfigurationSource corsSource,
            ObjectMapper objectMapper) throws Exception {

        http
                .csrf(csrf -> csrf.disable())

                .cors(cors ->
                        cors.configurationSource(corsSource))

                .sessionManagement(session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS))

                .authorizeHttpRequests(auth -> auth

                        .requestMatchers(
                                HttpMethod.OPTIONS, "/**")
                        .permitAll()

                        .requestMatchers("/api/auth/login")
                        .permitAll()

                        .requestMatchers("/api/admin/**")
                        .hasRole("ADMIN")

                        .requestMatchers("/api/owner/**")
                        .hasRole("OWNER")

                        .requestMatchers("/api/student/**")
                        .hasRole("STUDENT")

                        .anyRequest()
                        .authenticated()
                )

                .exceptionHandling(exception -> exception

                        .authenticationEntryPoint(
                                (request, response, error) -> {

                                    response.setStatus(401);
                                    response.setCharacterEncoding("UTF-8");
                                    response.setContentType(
                                            "application/json;charset=UTF-8");

                                    response.getWriter().write(
                                            objectMapper.writeValueAsString(
                                                    java.util.Map.of(
                                                            "code", 401,
                                                            "message",
                                                            "请先登录或重新登录"
                                                    )
                                            )
                                    );
                                }
                        )

                        .accessDeniedHandler(
                                (request, response, error) -> {

                                    response.setStatus(403);
                                    response.setCharacterEncoding("UTF-8");
                                    response.setContentType(
                                            "application/json;charset=UTF-8");

                                    response.getWriter().write(
                                            objectMapper.writeValueAsString(
                                                    java.util.Map.of(
                                                            "code", 403,
                                                            "message",
                                                            "没有访问权限"
                                                    )
                                            )
                                    );
                                }
                        )
                )

                .addFilterBefore(
                        jwtFilter,
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }

    // 防止 @Component JWT Filter 被 Servlet 容器重复注册
    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter>
    jwtFilterRegistration(JwtAuthenticationFilter filter) {

        FilterRegistrationBean<JwtAuthenticationFilter> registration =
                new FilterRegistrationBean<>(filter);

        registration.setEnabled(false);

        return registration;
    }
}

