package com.finrpa.auth.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT 工具类，封装 token 的生成、解析与校验
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Component
@ConfigurationProperties(prefix = "jwt")
@Getter
@Setter
public class JwtUtil {

    /** 签名密钥 */
    private String secret;

    /** 访问令牌过期时间（分钟） */
    private int accessTokenExpireMinutes;

    /** 刷新令牌过期时间（天） */
    private int refreshTokenExpireDays;

    /**
     * 获取签名密钥
     *
     * @return HMAC-SHA 密钥
     */
    private SecretKey getSigningKey() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * 生成访问令牌
     *
     * @param userId   用户 ID
     * @param username 用户名
     * @param orgId    组织 ID
     * @param deptName 部门名称
     * @return 访问令牌
     */
    public String generateAccessToken(String userId, String username, String orgId, String deptName) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("username", username);
        claims.put("orgId", orgId);
        claims.put("deptName", deptName);
        claims.put("tokenType", "access");

        Date now = new Date();
        Date expireTime = new Date(now.getTime() + accessTokenExpireMinutes * 60 * 1000L);

        return Jwts.builder()
                .claims(claims)
                .subject(username)
                .issuedAt(now)
                .expiration(expireTime)
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * 生成刷新令牌
     *
     * @param userId   用户 ID
     * @param username 用户名
     * @return 刷新令牌
     */
    public String generateRefreshToken(String userId, String username) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("username", username);
        claims.put("tokenType", "refresh");

        Date now = new Date();
        Date expireTime = new Date(now.getTime() + refreshTokenExpireDays * 24 * 60 * 60 * 1000L);

        return Jwts.builder()
                .claims(claims)
                .subject(username)
                .issuedAt(now)
                .expiration(expireTime)
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * 解析 token，返回 Claims
     *
     * @param token 令牌
     * @return Claims 载荷
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 校验 token 是否有效（未过期且签名正确）
     *
     * @param token 令牌
     * @return 是否有效
     */
    public boolean validateToken(String token) {
        try {
            Claims claims = parseToken(token);
            return !claims.getExpiration().before(new Date());
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 从 token 中获取用户 ID
     *
     * @param token 令牌
     * @return 用户 ID
     */
    public String getUserIdFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.get("userId", String.class);
    }

    /**
     * 从 token 中获取用户名
     *
     * @param token 令牌
     * @return 用户名
     */
    public String getUsernameFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.getSubject();
    }

    /**
     * 从 token 中获取组织 ID
     *
     * @param token 令牌
     * @return 组织 ID
     */
    public String getOrgIdFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.get("orgId", String.class);
    }

    /**
     * 从 token 中获取部门名称
     *
     * @param token 令牌
     * @return 部门名称
     */
    public String getDeptNameFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.get("deptName", String.class);
    }

    /**
     * 从 token 中获取令牌类型（access / refresh）
     *
     * @param token 令牌
     * @return 令牌类型
     */
    public String getTokenTypeFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.get("tokenType", String.class);
    }

    /**
     * 获取访问令牌的过期秒数
     *
     * @return 过期秒数
     */
    public long getExpiresIn() {
        return accessTokenExpireMinutes * 60L;
    }
}