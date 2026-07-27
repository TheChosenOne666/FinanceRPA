package com.finrpa.auth.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        jwtUtil.setSecret("test-secret-key-for-jwt-signing-must-be-at-least-32-characters");
        jwtUtil.setAccessTokenExpireMinutes(60);
        jwtUtil.setRefreshTokenExpireDays(7);
    }

    @Test
    @DisplayName("生成访问令牌 - 应包含正确的声明")
    void generateAccessToken_ShouldContainCorrectClaims() {
        String userId = "user-123";
        String username = "testuser";
        String orgId = "org-456";
        String deptName = "技术部";

        String token = jwtUtil.generateAccessToken(userId, username, orgId, deptName);

        assertThat(token).isNotBlank();
        assertThat(jwtUtil.getUserIdFromToken(token)).isEqualTo(userId);
        assertThat(jwtUtil.getUsernameFromToken(token)).isEqualTo(username);
        assertThat(jwtUtil.getOrgIdFromToken(token)).isEqualTo(orgId);
        assertThat(jwtUtil.getDeptNameFromToken(token)).isEqualTo(deptName);
        assertThat(jwtUtil.getTokenTypeFromToken(token)).isEqualTo("access");
    }

    @Test
    @DisplayName("生成刷新令牌 - 应包含正确的声明")
    void generateRefreshToken_ShouldContainCorrectClaims() {
        String userId = "user-123";
        String username = "testuser";

        String token = jwtUtil.generateRefreshToken(userId, username);

        assertThat(token).isNotBlank();
        assertThat(jwtUtil.getUserIdFromToken(token)).isEqualTo(userId);
        assertThat(jwtUtil.getUsernameFromToken(token)).isEqualTo(username);
        assertThat(jwtUtil.getTokenTypeFromToken(token)).isEqualTo("refresh");
    }

    @Test
    @DisplayName("验证令牌 - 有效的访问令牌应返回true")
    void validateToken_ValidToken_ShouldReturnTrue() {
        String token = jwtUtil.generateAccessToken("user-123", "testuser", "org-456", "技术部");

        boolean isValid = jwtUtil.validateToken(token);

        assertThat(isValid).isTrue();
    }

    @Test
    @DisplayName("验证令牌 - 无效的令牌应返回false")
    void validateToken_InvalidToken_ShouldReturnFalse() {
        boolean isValid = jwtUtil.validateToken("invalid-token-string");

        assertThat(isValid).isFalse();
    }

    @Test
    @DisplayName("获取过期时间 - 应返回正确的秒数")
    void getExpiresIn_ShouldReturnCorrectSeconds() {
        jwtUtil.setAccessTokenExpireMinutes(60);

        long expiresIn = jwtUtil.getExpiresIn();

        assertThat(expiresIn).isEqualTo(60 * 60L);
    }
}
