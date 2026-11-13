
package com.edu.ai.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtTokenProvider {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration:86400000}")
    private long expiration;

    // 获取 JWT 签名密钥
    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(
                secret.getBytes(StandardCharsets.UTF_8)
        );
    }

    // 生成 Token
    public String generateToken(Long userId, String role) {

        Date now = new Date();
        Date expireDate = new Date(now.getTime() + expiration);

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("role", role)
                .issuedAt(now)
                .expiration(expireDate)
                .signWith(getSigningKey())
                .compact();
    }

    // 解析 Token
    public Claims parseToken(String token) {

        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    // 获取用户 ID
    public Long getUserId(String token) {
        return Long.valueOf(
                parseToken(token).getSubject()
        );
    }

    // 验证 Token 是否有效
    public boolean validateToken(String token) {

        try {
            parseToken(token);
            return true;
        } catch (io.jsonwebtoken.JwtException
                 | IllegalArgumentException e) {
            return false;
        }
    }
}
