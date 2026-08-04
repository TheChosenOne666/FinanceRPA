package com.finrpa.auth.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.finrpa.auth.dto.request.SessionQueryRequest;
import com.finrpa.auth.dto.response.SessionVO;
import com.finrpa.auth.service.LoginPolicyService;
import com.finrpa.auth.service.SessionService;
import com.finrpa.auth.dto.response.LoginPolicyVO;
import com.finrpa.auth.util.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.annotation.Resource;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RKeys;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 会话管理服务实现（P2 SEC-3）
 *
 * <p>Redis 数据结构：
 * <ul>
 *   <li>黑名单：{@code finrpa:session:blacklist:{sessionId}} (String) → TTL = token 剩余有效期</li>
 *   <li>用户会话集合：{@code finrpa:session:user:{userId}} (RMap&lt;sessionId, SessionInfo&gt;) → TTL = access token 最大有效期</li>
 * </ul>
 * </p>
 *
 * <p>sessionId = SHA-256(token) 前 32 hex（不可逆，避免在 Redis 中存储原 token）。</p>
 *
 * <p>策略开关：{@link LoginPolicyService#getActivePolicy()} 返回 null（禁用或缺失）时，
 * 仅黑名单生效，会话集合校验与空闲超时检查自动跳过，兼容未启用 SEC-3 时已签发的 token。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Slf4j
@Service
public class SessionServiceImpl implements SessionService {

    /** 黑名单 Redis Key 前缀 */
    private static final String BLACKLIST_KEY_PREFIX = "finrpa:session:blacklist:";

    /** 用户会话集合 Redis Key 前缀 */
    private static final String USER_SESSION_KEY_PREFIX = "finrpa:session:user:";

    /** 黑名单占位值（仅校验 key 是否存在，不关心具体值） */
    private static final String BLACKLIST_VALUE = "1";

    /** Redisson 客户端 */
    @Resource
    private RedissonClient redissonClient;

    /** JWT 工具（解析 token 的 userId / username / 过期时间） */
    @Resource
    private JwtUtil jwtUtil;

    /** 登录策略服务（读取策略启用状态 / allowMultiLogin / sessionTimeoutMinutes） */
    @Resource
    private LoginPolicyService loginPolicyService;

    // region 会话 ID 计算

    /**
     * 计算 token 的会话 ID（SHA-256 前 32 hex）
     *
     * @param token JWT 访问令牌
     * @return 32 字符 hex 字符串
     */
    @Override
    public String getSessionId(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            // 取前 16 字节（128bit）转 hex（32 字符）
            StringBuilder sb = new StringBuilder(32);
            for (int i = 0; i < 16; i++) {
                sb.append(String.format("%02x", hash[i]));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 是 JDK 必备算法，不会缺失
            throw new IllegalStateException("SHA-256 算法不可用", e);
        }
    }

    // endregion

    // region 创建会话

    /**
     * 登录成功后创建会话
     *
     * <p>若登录策略启用 + allowMultiLogin=0，先踢掉该用户已有的所有会话，实现单端登录语义。</p>
     *
     * @param token     JWT 访问令牌
     * @param clientIp  登录客户端 IP
     * @param userAgent 客户端 User-Agent
     */
    @Override
    public void createSession(String token, String clientIp, String userAgent) {
        // 1. 解析 token 元数据
        Claims claims = jwtUtil.parseToken(token);
        String userId = claims.get("userId", String.class);
        String username = claims.getSubject();
        long expiresAtMillis = claims.getExpiration().getTime();
        long nowMillis = Instant.now().toEpochMilli();

        // 2. 计算 sessionId
        String sessionId = getSessionId(token);

        // 3. 构造 SessionInfo
        SessionInfo info = new SessionInfo();
        info.sessionId = sessionId;
        info.userId = userId;
        info.username = username;
        info.loginIp = clientIp;
        info.userAgent = userAgent;
        info.loginTime = nowMillis;
        info.lastAccessTime = nowMillis;
        info.expiresAt = expiresAtMillis;

        // 4. 取 access token 最大有效期（用于黑名单 TTL 与会话 hash TTL）
        long accessTokenMaxMs = jwtUtil.getExpiresIn() * 1000L;
        long remainingMs = expiresAtMillis - nowMillis;
        // 4.1 remainingMs 为正时用它（更精确），否则用 accessTokenMaxMs（保守值）
        long blacklistTtlMs = remainingMs > 0 ? remainingMs : accessTokenMaxMs;

        // 5. 单端登录：策略启用 + allowMultiLogin=0 时踢掉旧会话
        LoginPolicyVO policy = loginPolicyService.getActivePolicy();
        boolean enforceSingleLogin = policy != null
                && policy.getAllowMultiLogin() != null
                && policy.getAllowMultiLogin() == 0;
        if (enforceSingleLogin && userId != null) {
            killAllSessionsOfUser(userId, blacklistTtlMs);
        }

        // 6. 写入新会话
        if (userId != null) {
            RMap<String, SessionInfo> userSessions = redissonClient.getMap(USER_SESSION_KEY_PREFIX + userId);
            userSessions.put(sessionId, info);
            // 整个 hash 设置 TTL = access token 最大有效期，长时间未登录自动清理
            userSessions.expire(Duration.ofMillis(accessTokenMaxMs));
        }

        log.info("创建会话: userId={}, username={}, sessionId={}, enforceSingleLogin={}",
                userId, username, sessionId, enforceSingleLogin);
    }

    // endregion

    // region 销毁会话（登出）

    /**
     * 登出时销毁会话（拉黑当前 token + 从用户会话集合移除）
     *
     * @param token JWT 访问令牌
     */
    @Override
    public void destroySession(String token) {
        // 1. 解析 token
        Claims claims;
        try {
            claims = jwtUtil.parseToken(token);
        } catch (Exception e) {
            // token 已失效则无需拉黑
            log.debug("销毁会话时 token 已失效，跳过");
            return;
        }
        String userId = claims.get("userId", String.class);
        long expiresAtMillis = claims.getExpiration().getTime();
        long nowMillis = Instant.now().toEpochMilli();
        long remainingMs = expiresAtMillis - nowMillis;
        if (remainingMs <= 0) {
            // token 已过期，无需拉黑
            return;
        }

        // 2. 拉黑 sessionId，TTL = 剩余有效期
        String sessionId = getSessionId(token);
        RBucket<String> blacklistBucket = redissonClient.getBucket(BLACKLIST_KEY_PREFIX + sessionId);
        blacklistBucket.set(BLACKLIST_VALUE, Duration.ofMillis(remainingMs));

        // 3. 从用户会话集合移除
        if (userId != null) {
            RMap<String, SessionInfo> userSessions = redissonClient.getMap(USER_SESSION_KEY_PREFIX + userId);
            userSessions.remove(sessionId);
        }

        log.info("销毁会话: userId={}, sessionId={}, remainingMs={}",
                userId, sessionId, remainingMs);
    }

    // endregion

    // region 过滤器校验

    /**
     * JWT 过滤器每次请求调用，校验 token 是否仍可使用并刷新最后访问时间
     *
     * @param token JWT 访问令牌
     * @return true 表示 token 仍可使用；false 表示已失效
     */
    @Override
    public boolean touchSession(String token) {
        // 1. 计算 sessionId
        String sessionId = getSessionId(token);

        // 2. 黑名单校验
        RBucket<String> blacklistBucket = redissonClient.getBucket(BLACKLIST_KEY_PREFIX + sessionId);
        if (blacklistBucket.isExists()) {
            log.debug("会话命中黑名单: sessionId={}", sessionId);
            return false;
        }

        // 3. 策略禁用时仅黑名单生效（兼容未启用 SEC-3 时已签发的 token）
        LoginPolicyVO policy = loginPolicyService.getActivePolicy();
        if (policy == null) {
            return true;
        }

        // 4. 解析 token 拿 userId
        Claims claims;
        try {
            claims = jwtUtil.parseToken(token);
        } catch (Exception e) {
            // token 解析失败交给 JwtAuthenticationFilter 的 validateToken 处理
            return true;
        }
        String userId = claims.get("userId", String.class);
        if (userId == null) {
            // 无 userId 的 token 不做会话校验（兼容旧 token）
            return true;
        }

        // 5. 会话集合校验
        RMap<String, SessionInfo> userSessions = redissonClient.getMap(USER_SESSION_KEY_PREFIX + userId);
        SessionInfo info = userSessions.get(sessionId);
        if (info == null) {
            // 会话不存在（已被踢或登出）
            log.debug("会话集合中不存在: userId={}, sessionId={}", userId, sessionId);
            return false;
        }

        // 6. 空闲超时校验
        Integer timeoutMinutes = policy.getSessionTimeoutMinutes();
        if (timeoutMinutes != null && timeoutMinutes > 0) {
            long nowMillis = Instant.now().toEpochMilli();
            long idleMs = nowMillis - info.lastAccessTime;
            if (idleMs > timeoutMinutes * 60_000L) {
                // 空闲超时，销毁会话
                userSessions.remove(sessionId);
                long remainingMs = info.expiresAt - nowMillis;
                if (remainingMs > 0) {
                    redissonClient.getBucket(BLACKLIST_KEY_PREFIX + sessionId)
                            .set(BLACKLIST_VALUE, Duration.ofMillis(remainingMs));
                }
                log.info("会话空闲超时自动下线: userId={}, sessionId={}, idleMs={}, thresholdMs={}",
                        userId, sessionId, idleMs, timeoutMinutes * 60_000L);
                return false;
            }
        }

        // 7. 刷新最后访问时间
        info.lastAccessTime = Instant.now().toEpochMilli();
        userSessions.put(sessionId, info);

        return true;
    }

    // endregion

    // region 管理员查询

    /**
     * 查询在线会话列表（按 userId / username 筛选 + 分页）
     *
     * <p>使用 Redisson SCAN 遍历所有用户会话 hash，避免 KEYS 阻塞 Redis。</p>
     *
     * @param queryRequest 查询请求
     * @return 分页结果（含总条数与当前页数据）
     */
    @Override
    public IPage<SessionVO> listSessions(SessionQueryRequest queryRequest) {
        // 1. 收集所有会话
        List<SessionVO> all = new ArrayList<>();
        RKeys keys = redissonClient.getKeys();
        Iterable<String> hashKeys = keys.getKeysByPattern(USER_SESSION_KEY_PREFIX + "*", 100);
        for (String hashKey : hashKeys) {
            RMap<String, SessionInfo> userSessions = redissonClient.getMap(hashKey);
            Collection<SessionInfo> infos = userSessions.values();
            for (SessionInfo info : infos) {
                all.add(toVO(info));
            }
        }

        // 2. 筛选
        List<SessionVO> filtered = new ArrayList<>(all.size());
        String userIdFilter = queryRequest.getUserId();
        String usernameFilter = queryRequest.getUsername();
        for (SessionVO vo : all) {
            if (userIdFilter != null && !userIdFilter.isEmpty()
                    && !userIdFilter.equals(vo.getUserId())) {
                continue;
            }
            if (usernameFilter != null && !usernameFilter.isEmpty()
                    && (vo.getUsername() == null
                    || !vo.getUsername().toLowerCase().contains(usernameFilter.toLowerCase()))) {
                continue;
            }
            filtered.add(vo);
        }

        // 3. 按 loginTime 倒序
        filtered.sort((a, b) -> {
            long ta = a.getLoginTime() == null ? 0 : a.getLoginTime().getTime();
            long tb = b.getLoginTime() == null ? 0 : b.getLoginTime().getTime();
            return Long.compare(tb, ta);
        });

        // 4. 分页
        int current = Math.max(queryRequest.getCurrent(), 1);
        int pageSize = Math.max(queryRequest.getPageSize(), 1);
        int from = (current - 1) * pageSize;
        int to = Math.min(from + pageSize, filtered.size());
        List<SessionVO> pageData = from >= filtered.size() ? List.of() : filtered.subList(from, to);

        // 5. 构造 IPage
        Page<SessionVO> page = new Page<>(current, pageSize, filtered.size());
        page.setRecords(pageData);
        return page;
    }

    // endregion

    // region 管理员踢人

    /**
     * 管理员踢人下线（按 sessionId 销毁会话）
     *
     * <p>sessionId 不携带 userId 信息，需要遍历所有用户 hash 查找。
     * 用户量 &lt; 1000 时性能可接受；如需优化可在踢人时由前端附带 userId query 参数。</p>
     *
     * @param sessionId 会话 ID
     * @return true 表示踢出成功；false 表示会话不存在
     */
    @Override
    public boolean killSession(String sessionId) {
        // 1. 遍历所有用户会话 hash，定位 sessionId
        RKeys keys = redissonClient.getKeys();
        Iterable<String> hashKeys = keys.getKeysByPattern(USER_SESSION_KEY_PREFIX + "*", 100);
        for (String hashKey : hashKeys) {
            RMap<String, SessionInfo> userSessions = redissonClient.getMap(hashKey);
            SessionInfo info = userSessions.get(sessionId);
            if (info != null) {
                // 2. 加入黑名单，TTL = 剩余有效期
                long nowMillis = Instant.now().toEpochMilli();
                long remainingMs = info.expiresAt - nowMillis;
                if (remainingMs > 0) {
                    redissonClient.getBucket(BLACKLIST_KEY_PREFIX + sessionId)
                            .set(BLACKLIST_VALUE, Duration.ofMillis(remainingMs));
                }
                // 3. 从会话集合移除
                userSessions.remove(sessionId);
                log.info("管理员踢下线: userId={}, username={}, sessionId={}",
                        info.userId, info.username, sessionId);
                return true;
            }
        }
        return false;
    }

    // endregion

    // region 私有方法

    /**
     * 踢掉指定用户的所有在线会话（用于单端登录语义）
     *
     * @param userId      用户 ID
     * @param blacklistTtlMs 黑名单 TTL（毫秒）
     */
    private void killAllSessionsOfUser(String userId, long blacklistTtlMs) {
        RMap<String, SessionInfo> userSessions = redissonClient.getMap(USER_SESSION_KEY_PREFIX + userId);
        Map<String, SessionInfo> all = new HashMap<>(userSessions.readAllMap());
        if (all.isEmpty()) {
            return;
        }
        // 1. 所有旧会话加入黑名单
        for (String sessionId : all.keySet()) {
            redissonClient.getBucket(BLACKLIST_KEY_PREFIX + sessionId)
                    .set(BLACKLIST_VALUE, Duration.ofMillis(blacklistTtlMs));
        }
        // 2. 清空会话集合
        userSessions.clear();
        log.info("单端登录踢掉旧会话: userId={}, count={}", userId, all.size());
    }

    /**
     * SessionInfo → SessionVO 转换
     *
     * @param info 会话信息
     * @return 会话 VO
     */
    private SessionVO toVO(SessionInfo info) {
        SessionVO vo = new SessionVO();
        vo.setSessionId(info.sessionId);
        vo.setUserId(info.userId);
        vo.setUsername(info.username);
        vo.setLoginIp(info.loginIp);
        vo.setLoginTime(info.loginTime > 0 ? new Timestamp(info.loginTime) : null);
        vo.setLastAccessTime(info.lastAccessTime > 0 ? new Timestamp(info.lastAccessTime) : null);
        vo.setExpiresAt(info.expiresAt > 0 ? new Timestamp(info.expiresAt) : null);
        vo.setUserAgent(info.userAgent);
        return vo;
    }

    // endregion

    // region 内部会话信息类（Redis 存储）

    /**
     * 会话信息（Redis 存储）
     *
     * <p>使用 long 而非 Timestamp，便于 Jackson 序列化与算术运算。</p>
     */
    @Data
    public static class SessionInfo {
        /** 会话 ID（SHA-256 前 32 hex） */
        private String sessionId;
        /** 用户业务 ID */
        private String userId;
        /** 用户名 */
        private String username;
        /** 登录客户端 IP */
        private String loginIp;
        /** 登录时间（epoch 毫秒） */
        private long loginTime;
        /** 最后访问时间（epoch 毫秒，每次请求刷新） */
        private long lastAccessTime;
        /** token 过期时间（epoch 毫秒） */
        private long expiresAt;
        /** 客户端 User-Agent */
        private String userAgent;

        /**
         * equals / hashCode 仅基于 sessionId（同一 token 同一会话）
         */
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof SessionInfo that)) return false;
            return Objects.equals(sessionId, that.sessionId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(sessionId);
        }
    }

    // endregion
}
