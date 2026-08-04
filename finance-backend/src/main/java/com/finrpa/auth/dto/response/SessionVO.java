package com.finrpa.auth.dto.response;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.sql.Timestamp;

/**
 * 在线会话视图对象（P2 SEC-3）
 *
 * <p>由 {@link com.finrpa.auth.service.SessionService#listSessions} 返回，
 * 用于设置页「安全策略 · 在线会话」展示与踢人操作。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
public class SessionVO implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 会话 ID（token SHA-256 前 32 hex） */
    private String sessionId;

    /** 用户业务 ID */
    private String userId;

    /** 用户名 */
    private String username;

    /** 登录客户端 IP */
    private String loginIp;

    /** 登录时间 */
    private Timestamp loginTime;

    /** 最后访问时间（每次请求过滤器会刷新） */
    private Timestamp lastAccessTime;

    /** token 过期时间 */
    private Timestamp expiresAt;

    /** 客户端 User-Agent（用于识别设备类型） */
    private String userAgent;
}
