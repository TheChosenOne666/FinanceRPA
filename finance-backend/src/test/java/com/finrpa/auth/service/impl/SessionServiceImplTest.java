package com.finrpa.auth.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.finrpa.auth.dto.request.SessionQueryRequest;
import com.finrpa.auth.dto.response.LoginPolicyVO;
import com.finrpa.auth.dto.response.SessionVO;
import com.finrpa.auth.service.LoginPolicyService;
import com.finrpa.auth.util.JwtUtil;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RBucket;
import org.redisson.api.RKeys;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 会话管理服务实现单元测试（P2 SEC-3）
 *
 * <p>测试覆盖 sessionId 计算 / 会话创建 / 销毁 / 过滤器校验 / 列表查询 / 踢人六大核心场景，
 * 策略启用与禁用分支均验证。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@ExtendWith(MockitoExtension.class)
class SessionServiceImplTest {

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private LoginPolicyService loginPolicyService;

    @Mock
    private RMap<String, SessionServiceImpl.SessionInfo> userSessionMap;

    @Mock
    private RBucket<String> blacklistBucket;

    @Mock
    private RKeys rKeys;

    @InjectMocks
    private SessionServiceImpl sessionService;

    /** 测试用 token */
    private static final String TOKEN = "test-access-token-for-p2-sec3";

    /** 测试用 sessionId（与 TOKEN 对应） */
    private String sessionId;

    /** 构造启用的登录策略（不允许并发，空闲 30 分钟） */
    private LoginPolicyVO buildEnabledPolicy() {
        LoginPolicyVO vo = new LoginPolicyVO();
        vo.setPolicyId(1751000000000000011L);
        vo.setMaxLoginAttempts(5);
        vo.setLockMinutes(30);
        vo.setAllowMultiLogin(0);
        vo.setSessionTimeoutMinutes(30);
        vo.setEnabled(1);
        return vo;
    }

    /** 构造禁用的登录策略 */
    private LoginPolicyVO buildDisabledPolicy() {
        LoginPolicyVO vo = buildEnabledPolicy();
        vo.setEnabled(0);
        return vo;
    }

    /** 构造 Claims mock，含 userId=username=expiration */
    private Claims buildClaims(String userId, String username, long expiresAtMillis) {
        Claims claims = org.mockito.Mockito.mock(Claims.class);
        lenient().when(claims.get("userId", String.class)).thenReturn(userId);
        lenient().when(claims.getSubject()).thenReturn(username);
        Date expiration = new Date(expiresAtMillis);
        lenient().when(claims.getExpiration()).thenReturn(expiration);
        return claims;
    }

    @BeforeEach
    void setUp() {
        sessionId = sessionService.getSessionId(TOKEN);
        // 默认 stubbing
        lenient().when(redissonClient.<String>getBucket(anyString())).thenReturn(blacklistBucket);
        lenient().when(redissonClient.<String, SessionServiceImpl.SessionInfo>getMap(anyString()))
                .thenReturn(userSessionMap);
        lenient().when(redissonClient.getKeys()).thenReturn(rKeys);
        // JwtUtil.getExpiresIn 默认 60 分钟 = 3600_000 ms
        lenient().when(jwtUtil.getExpiresIn()).thenReturn(3600L);
    }

    // region getSessionId

    @Test
    @DisplayName("getSessionId - 长度为 32 hex")
    void getSessionId_Returns32Hex() {
        String sid = sessionService.getSessionId(TOKEN);
        assertThat(sid).hasSize(32);
        assertThat(sid).matches("[0-9a-f]{32}");
    }

    @Test
    @DisplayName("getSessionId - 相同 token 返回相同 sessionId")
    void getSessionId_SameToken_ReturnsSameId() {
        String sid1 = sessionService.getSessionId(TOKEN);
        String sid2 = sessionService.getSessionId(TOKEN);
        assertThat(sid1).isEqualTo(sid2);
    }

    @Test
    @DisplayName("getSessionId - 不同 token 返回不同 sessionId")
    void getSessionId_DifferentToken_ReturnsDifferentId() {
        String sid1 = sessionService.getSessionId(TOKEN);
        String sid2 = sessionService.getSessionId("another-token");
        assertThat(sid1).isNotEqualTo(sid2);
    }

    // endregion

    // region createSession

    @Test
    @DisplayName("createSession - 策略禁用时不踢旧会话，仅写入新会话")
    void createSession_DisabledPolicy_OnlyWritesNewSession() {
        long expiresAt = Instant.now().plus(60, java.time.temporal.ChronoUnit.MINUTES).toEpochMilli();
        Claims claims = buildClaims("100", "alice", expiresAt);
        when(jwtUtil.parseToken(TOKEN)).thenReturn(claims);
        when(loginPolicyService.getActivePolicy()).thenReturn(null);

        sessionService.createSession(TOKEN, "192.168.1.1", "Mozilla/5.0");

        // 验证：写入会话集合
        verify(userSessionMap).put(eq(sessionId), any(SessionServiceImpl.SessionInfo.class));
        verify(userSessionMap).expire(any(Duration.class));
        // 验证：未踢旧会话（readAllMap 未被调用）
        verify(userSessionMap, never()).readAllMap();
    }

    @Test
    @DisplayName("createSession - 策略启用 + allowMultiLogin=0 时踢掉旧会话")
    void createSession_EnforceSingleLogin_KicksOldSessions() {
        long expiresAt = Instant.now().plus(60, java.time.temporal.ChronoUnit.MINUTES).toEpochMilli();
        Claims claims = buildClaims("100", "alice", expiresAt);
        when(jwtUtil.parseToken(TOKEN)).thenReturn(claims);
        when(loginPolicyService.getActivePolicy()).thenReturn(buildEnabledPolicy());

        // 模拟已有 2 个旧会话
        Map<String, SessionServiceImpl.SessionInfo> oldSessions = new HashMap<>();
        SessionServiceImpl.SessionInfo old1 = new SessionServiceImpl.SessionInfo();
        old1.setSessionId("old-session-1");
        old1.setExpiresAt(expiresAt);
        oldSessions.put("old-session-1", old1);
        when(userSessionMap.readAllMap()).thenReturn(oldSessions);

        sessionService.createSession(TOKEN, "192.168.1.1", "Mozilla/5.0");

        // 验证：旧会话加入黑名单
        verify(redissonClient).getBucket("finrpa:session:blacklist:old-session-1");
        verify(blacklistBucket).set(eq("1"), any(Duration.class));
        // 验证：清空旧会话集合
        verify(userSessionMap).clear();
        // 验证：写入新会话
        verify(userSessionMap).put(eq(sessionId), any(SessionServiceImpl.SessionInfo.class));
    }

    @Test
    @DisplayName("createSession - 策略启用 + allowMultiLogin=1 时直接写入")
    void createSession_AllowMultiLogin_WritesDirectly() {
        long expiresAt = Instant.now().plus(60, java.time.temporal.ChronoUnit.MINUTES).toEpochMilli();
        Claims claims = buildClaims("100", "alice", expiresAt);
        when(jwtUtil.parseToken(TOKEN)).thenReturn(claims);
        LoginPolicyVO policy = buildEnabledPolicy();
        policy.setAllowMultiLogin(1);
        when(loginPolicyService.getActivePolicy()).thenReturn(policy);

        sessionService.createSession(TOKEN, "192.168.1.1", "Mozilla/5.0");

        // 验证：未踢旧会话
        verify(userSessionMap, never()).readAllMap();
        verify(userSessionMap, never()).clear();
        // 验证：写入新会话
        verify(userSessionMap).put(eq(sessionId), any(SessionServiceImpl.SessionInfo.class));
    }

    // endregion

    // region destroySession

    @Test
    @DisplayName("destroySession - token 有效时拉黑 + 移除会话")
    void destroySession_ValidToken_BlacklistsAndRemoves() {
        long expiresAt = Instant.now().plus(30, java.time.temporal.ChronoUnit.MINUTES).toEpochMilli();
        Claims claims = buildClaims("100", "alice", expiresAt);
        when(jwtUtil.parseToken(TOKEN)).thenReturn(claims);

        sessionService.destroySession(TOKEN);

        // 验证：加入黑名单
        verify(blacklistBucket).set(eq("1"), any(Duration.class));
        // 验证：从会话集合移除
        verify(userSessionMap).remove(sessionId);
    }

    @Test
    @DisplayName("destroySession - token 已过期时跳过")
    void destroySession_ExpiredToken_Skips() {
        long expiresAt = Instant.now().minus(1, java.time.temporal.ChronoUnit.MINUTES).toEpochMilli();
        Claims claims = buildClaims("100", "alice", expiresAt);
        when(jwtUtil.parseToken(TOKEN)).thenReturn(claims);

        sessionService.destroySession(TOKEN);

        // 验证：未拉黑、未移除
        verify(blacklistBucket, never()).set(anyString(), any(Duration.class));
        verify(userSessionMap, never()).remove(anyString());
    }

    // endregion

    // region touchSession

    @Test
    @DisplayName("touchSession - 黑名单命中返回 false")
    void touchSession_Blacklisted_ReturnsFalse() {
        when(blacklistBucket.isExists()).thenReturn(true);

        boolean result = sessionService.touchSession(TOKEN);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("touchSession - 策略禁用时返回 true（仅黑名单生效）")
    void touchSession_DisabledPolicy_ReturnsTrue() {
        when(blacklistBucket.isExists()).thenReturn(false);
        when(loginPolicyService.getActivePolicy()).thenReturn(null);

        boolean result = sessionService.touchSession(TOKEN);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("touchSession - 会话集合中不存在返回 false")
    void touchSession_SessionNotInSet_ReturnsFalse() {
        when(blacklistBucket.isExists()).thenReturn(false);
        when(loginPolicyService.getActivePolicy()).thenReturn(buildEnabledPolicy());

        long expiresAt = Instant.now().plus(30, java.time.temporal.ChronoUnit.MINUTES).toEpochMilli();
        Claims claims = buildClaims("100", "alice", expiresAt);
        when(jwtUtil.parseToken(TOKEN)).thenReturn(claims);
        when(userSessionMap.get(sessionId)).thenReturn(null);

        boolean result = sessionService.touchSession(TOKEN);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("touchSession - 会话存在 + 未超时返回 true 并刷新访问时间")
    void touchSession_SessionValid_ReturnsTrueAndRefreshes() {
        when(blacklistBucket.isExists()).thenReturn(false);
        when(loginPolicyService.getActivePolicy()).thenReturn(buildEnabledPolicy());

        long now = Instant.now().toEpochMilli();
        long expiresAt = now + 30 * 60_000L;
        Claims claims = buildClaims("100", "alice", expiresAt);
        when(jwtUtil.parseToken(TOKEN)).thenReturn(claims);

        SessionServiceImpl.SessionInfo info = new SessionServiceImpl.SessionInfo();
        info.setSessionId(sessionId);
        info.setUserId("100");
        info.setUsername("alice");
        info.setLoginTime(now - 10 * 60_000L);
        info.setLastAccessTime(now - 5 * 60_000L); // 5 分钟前访问，未超 30 分钟
        info.setExpiresAt(expiresAt);
        when(userSessionMap.get(sessionId)).thenReturn(info);

        boolean result = sessionService.touchSession(TOKEN);

        assertThat(result).isTrue();
        // 验证：刷新了访问时间（重新 put 写回）
        verify(userSessionMap).put(eq(sessionId), any(SessionServiceImpl.SessionInfo.class));
    }

    @Test
    @DisplayName("touchSession - 会话存在 + 空闲超时返回 false 并销毁会话")
    void touchSession_IdleTimeout_ReturnsFalseAndDestroys() {
        when(blacklistBucket.isExists()).thenReturn(false);
        when(loginPolicyService.getActivePolicy()).thenReturn(buildEnabledPolicy());

        long now = Instant.now().toEpochMilli();
        long expiresAt = now + 30 * 60_000L;
        Claims claims = buildClaims("100", "alice", expiresAt);
        when(jwtUtil.parseToken(TOKEN)).thenReturn(claims);

        SessionServiceImpl.SessionInfo info = new SessionServiceImpl.SessionInfo();
        info.setSessionId(sessionId);
        info.setUserId("100");
        info.setUsername("alice");
        info.setLoginTime(now - 60 * 60_000L);
        info.setLastAccessTime(now - 45 * 60_000L); // 45 分钟前访问，超 30 分钟
        info.setExpiresAt(expiresAt);
        when(userSessionMap.get(sessionId)).thenReturn(info);

        boolean result = sessionService.touchSession(TOKEN);

        assertThat(result).isFalse();
        // 验证：销毁会话
        verify(userSessionMap).remove(sessionId);
        // 验证：加入黑名单
        verify(blacklistBucket).set(eq("1"), any(Duration.class));
    }

    // endregion

    // region listSessions

    @Test
    @DisplayName("listSessions - 返回所有会话并按 loginTime 倒序分页")
    void listSessions_ReturnsSortedAndPaged() {
        // 模拟 Redisson 扫描到 1 个用户会话 hash
        when(rKeys.getKeysByPattern(eq("finrpa:session:user:*"), eq(100)))
                .thenReturn(Collections.singleton("finrpa:session:user:100"));

        long now = Instant.now().toEpochMilli();
        Map<String, SessionServiceImpl.SessionInfo> sessions = new HashMap<>();
        SessionServiceImpl.SessionInfo s1 = new SessionServiceImpl.SessionInfo();
        s1.setSessionId("sid-1");
        s1.setUserId("100");
        s1.setUsername("alice");
        s1.setLoginTime(now - 10 * 60_000L);
        s1.setLastAccessTime(now - 5 * 60_000L);
        s1.setExpiresAt(now + 30 * 60_000L);
        sessions.put("sid-1", s1);

        SessionServiceImpl.SessionInfo s2 = new SessionServiceImpl.SessionInfo();
        s2.setSessionId("sid-2");
        s2.setUserId("100");
        s2.setUsername("alice");
        s2.setLoginTime(now - 5 * 60_000L);
        s2.setLastAccessTime(now - 2 * 60_000L);
        s2.setExpiresAt(now + 30 * 60_000L);
        sessions.put("sid-2", s2);

        when(userSessionMap.values()).thenReturn(sessions.values());

        SessionQueryRequest query = new SessionQueryRequest();
        query.setCurrent(1);
        query.setPageSize(10);
        IPage<SessionVO> page = sessionService.listSessions(query);

        assertThat(page.getTotal()).isEqualTo(2);
        assertThat(page.getRecords()).hasSize(2);
        // 按 loginTime 倒序：s2 应在前
        assertThat(page.getRecords().get(0).getSessionId()).isEqualTo("sid-2");
        assertThat(page.getRecords().get(1).getSessionId()).isEqualTo("sid-1");
    }

    @Test
    @DisplayName("listSessions - 按 username 筛选")
    void listSessions_FilterByUsername() {
        when(rKeys.getKeysByPattern(eq("finrpa:session:user:*"), eq(100)))
                .thenReturn(Collections.singleton("finrpa:session:user:100"));

        long now = Instant.now().toEpochMilli();
        Map<String, SessionServiceImpl.SessionInfo> sessions = new HashMap<>();
        SessionServiceImpl.SessionInfo s1 = new SessionServiceImpl.SessionInfo();
        s1.setSessionId("sid-1");
        s1.setUserId("100");
        s1.setUsername("alice");
        s1.setLoginTime(now - 10 * 60_000L);
        sessions.put("sid-1", s1);

        SessionServiceImpl.SessionInfo s2 = new SessionServiceImpl.SessionInfo();
        s2.setSessionId("sid-2");
        s2.setUserId("100");
        s2.setUsername("bob");
        s2.setLoginTime(now - 5 * 60_000L);
        sessions.put("sid-2", s2);

        when(userSessionMap.values()).thenReturn(sessions.values());

        SessionQueryRequest query = new SessionQueryRequest();
        query.setCurrent(1);
        query.setPageSize(10);
        query.setUsername("ali");
        IPage<SessionVO> page = sessionService.listSessions(query);

        assertThat(page.getTotal()).isEqualTo(1);
        assertThat(page.getRecords().get(0).getUsername()).isEqualTo("alice");
    }

    // endregion

    // region killSession

    @Test
    @DisplayName("killSession - 会话存在时加入黑名单并返回 true")
    void killSession_Exists_BlacklistsAndReturnsTrue() {
        when(rKeys.getKeysByPattern(eq("finrpa:session:user:*"), eq(100)))
                .thenReturn(Collections.singleton("finrpa:session:user:100"));

        long now = Instant.now().toEpochMilli();
        SessionServiceImpl.SessionInfo info = new SessionServiceImpl.SessionInfo();
        info.setSessionId("sid-1");
        info.setUserId("100");
        info.setUsername("alice");
        info.setExpiresAt(now + 30 * 60_000L);
        when(userSessionMap.get("sid-1")).thenReturn(info);

        boolean result = sessionService.killSession("sid-1");

        assertThat(result).isTrue();
        verify(blacklistBucket).set(eq("1"), any(Duration.class));
        verify(userSessionMap).remove("sid-1");
    }

    @Test
    @DisplayName("killSession - 会话不存在时返回 false")
    void killSession_NotExists_ReturnsFalse() {
        when(rKeys.getKeysByPattern(eq("finrpa:session:user:*"), eq(100)))
                .thenReturn(Collections.singleton("finrpa:session:user:100"));
        when(userSessionMap.get("nonexistent-sid")).thenReturn(null);

        boolean result = sessionService.killSession("nonexistent-sid");

        assertThat(result).isFalse();
        verify(blacklistBucket, never()).set(anyString(), any(Duration.class));
        verify(userSessionMap, never()).remove(anyString());
    }

    // endregion
}
