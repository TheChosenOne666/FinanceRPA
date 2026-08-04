package com.finrpa.auth.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.finrpa.auth.dto.request.SessionQueryRequest;
import com.finrpa.auth.dto.response.SessionVO;

/**
 * 会话管理服务接口（P2 SEC-3）
 *
 * <p>基于 Redis 维护 token 黑名单与用户在线会话集合，提供以下能力：
 * <ul>
 *   <li>{@link #createSession}：登录成功后写入会话；若策略启用 + 禁止并发登录，先踢掉旧会话</li>
 *   <li>{@link #destroySession}：登出时拉黑当前 token + 移除会话集合</li>
 *   <li>{@link #touchSession}：JWT 过滤器每次请求调用，校验黑名单 / 会话存在性 / 空闲超时，并刷新最后访问时间</li>
 *   <li>{@link #listSessions}：管理员视角查询在线会话列表</li>
 *   <li>{@link #killSession}：管理员视角踢人下线</li>
 * </ul>
 * </p>
 *
 * <p>策略开关：当 {@link com.finrpa.auth.service.LoginPolicyService#getActivePolicy()} 返回 null 或 enabled=0 时，
 * 仅黑名单生效（用于登出 / 踢人），会话集合校验与空闲超时检查自动跳过，兼容未启用 SEC-3 时已签发的 token。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
public interface SessionService {

    /**
     * 计算 token 的会话 ID（SHA-256 前 32 hex，不可逆，避免在 Redis 中存储原 token）
     *
     * @param token JWT 访问令牌
     * @return 会话 ID（32 字符 hex 字符串）
     */
    String getSessionId(String token);

    /**
     * 登录成功后创建会话
     *
     * <p>若登录策略启用 + allowMultiLogin=0，会先把该用户已有的所有会话加入黑名单并清空会话集合，
     * 实现「单端登录」语义。</p>
     *
     * @param token     JWT 访问令牌
     * @param clientIp  登录客户端 IP
     * @param userAgent 客户端 User-Agent
     */
    void createSession(String token, String clientIp, String userAgent);

    /**
     * 登出时销毁会话（拉黑当前 token + 从用户会话集合移除）
     *
     * @param token JWT 访问令牌
     */
    void destroySession(String token);

    /**
     * JWT 过滤器每次请求调用，校验 token 是否仍可使用并刷新最后访问时间
     *
     * <p>校验顺序：
     * <ol>
     *   <li>命中黑名单 → 返回 false</li>
     *   <li>策略禁用 → 返回 true（仅黑名单生效，兼容老 token）</li>
     *   <li>会话集合中不存在该 sessionId → 返回 false（已被踢或登出）</li>
     *   <li>空闲超时 → 销毁会话并返回 false</li>
     *   <li>通过 → 更新 lastAccessTime 并返回 true</li>
     * </ol>
     * </p>
     *
     * @param token JWT 访问令牌
     * @return true 表示 token 仍可使用；false 表示已失效（过滤器应清空 SecurityContext）
     */
    boolean touchSession(String token);

    /**
     * 查询在线会话列表（管理员视角，按 userId / username 筛选 + 分页）
     *
     * @param queryRequest 查询请求
     * @return 分页结果（含总条数与当前页数据）
     */
    IPage<SessionVO> listSessions(SessionQueryRequest queryRequest);

    /**
     * 管理员踢人下线（按 sessionId 销毁会话）
     *
     * @param sessionId 会话 ID
     * @return true 表示踢出成功；false 表示会话不存在
     */
    boolean killSession(String sessionId);
}
