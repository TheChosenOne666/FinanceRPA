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

@Component
@ConfigurationProperties(prefix = "jwt")
@Getter
@Setter
public class JwtUtil {

    private String secret;

    private int accessTokenExpireMinutes;

    private int refreshTokenExpireDays;

    private SecretKey getSigningKey() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

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

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean validateToken(String token) {
        try {
            Claims claims = parseToken(token);
            return !claims.getExpiration().before(new Date());
        } catch (Exception e) {
            return false;
        }
    }

    public String getUserIdFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.get("userId", String.class);
    }

    public String getUsernameFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.getSubject();
    }

    public String getOrgIdFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.get("orgId", String.class);
    }

    public String getDeptNameFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.get("deptName", String.class);
    }

    public String getTokenTypeFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.get("tokenType", String.class);
    }

    public long getExpiresIn() {
        return accessTokenExpireMinutes * 60L;
    }
}