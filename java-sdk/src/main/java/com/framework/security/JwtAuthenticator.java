package com.framework.security;

import com.framework.exception.ErrorCode;
import com.framework.exception.FrameworkException;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

/**
 * JWT 认证器
 * 
 * 基于 jjwt 实现 JWT 令牌的生成和验证。
 * 支持自定义 claims、过期时间和角色信息。
 * 
 * 需求: 11.2, 11.4
 */
public class JwtAuthenticator {

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticator.class);

    private final SecretKey secretKey;
    private final long defaultExpirationMs;
    private final String issuer;

    public JwtAuthenticator(String secret, long defaultExpirationMs, String issuer) {
        // 确保密钥长度足够（至少 256 位）
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            byte[] padded = new byte[32];
            System.arraycopy(keyBytes, 0, padded, 0, Math.min(keyBytes.length, 32));
            keyBytes = padded;
        }
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
        this.defaultExpirationMs = defaultExpirationMs;
        this.issuer = issuer;
    }

    public JwtAuthenticator(String secret) {
        this(secret, 3600000, "framework"); // 默认 1 小时过期
    }

    /**
     * 生成 JWT 令牌
     */
    public String generateToken(String subject, Map<String, Object> claims) {
        Instant now = Instant.now();
        JwtBuilder builder = Jwts.builder()
                .subject(subject)
                .issuer(issuer)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(defaultExpirationMs)));

        if (claims != null) {
            claims.forEach(builder::claim);
        }

        return builder.signWith(secretKey).compact();
    }

    /**
     * 生成带角色的 JWT 令牌
     */
    public String generateToken(String subject, List<String> roles) {
        return generateToken(subject, Map.of("roles", roles));
    }

    /**
     * 验证 JWT 令牌
     * 
     * @return 解析后的 Claims
     * @throws FrameworkException 如果令牌无效或过期
     */
    public Claims validateToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            throw new FrameworkException(ErrorCode.UNAUTHORIZED, "JWT 令牌已过期");
        } catch (JwtException e) {
            throw new FrameworkException(ErrorCode.UNAUTHORIZED, "JWT 令牌无效: " + e.getMessage());
        }
    }

    /**
     * 从令牌中提取主题（用户标识）
     */
    public String getSubject(String token) {
        return validateToken(token).getSubject();
    }

    /**
     * 从令牌中提取角色列表
     */
    @SuppressWarnings("unchecked")
    public List<String> getRoles(String token) {
        Claims claims = validateToken(token);
        Object roles = claims.get("roles");
        if (roles instanceof List) {
            return (List<String>) roles;
        }
        return Collections.emptyList();
    }

    /**
     * 检查令牌是否即将过期（在指定时间窗口内）
     */
    public boolean isExpiringSoon(String token, long windowMs) {
        try {
            Claims claims = validateToken(token);
            Date expiration = claims.getExpiration();
            return expiration.getTime() - System.currentTimeMillis() < windowMs;
        } catch (FrameworkException e) {
            return true;
        }
    }
}
