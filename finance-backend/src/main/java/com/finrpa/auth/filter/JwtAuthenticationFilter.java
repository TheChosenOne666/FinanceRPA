package com.finrpa.auth.filter;

import com.finrpa.agent.constant.AgentConstant;
import com.finrpa.auth.service.SessionService;
import com.finrpa.auth.util.JwtUtil;
import com.finrpa.tenant.constant.TenantConstant;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * JWT 认证过滤器，从请求头解析 token 并写入 SecurityContext
 *
 * <p>同时将 token 中的 orgId 暂存到 request attribute（key 见 {@link TenantConstant#ORG_ID_REQUEST_ATTR}），
 * 供后续的 {@link com.finrpa.tenant.interceptor.TenantInterceptor} 注入 {@link com.finrpa.tenant.context.TenantContext}。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** JWT 工具 */
    private final JwtUtil jwtUtil;

    /** 会话管理服务（P2 SEC-3，校验黑名单 / 会话集合 / 空闲超时） */
    private final SessionService sessionService;

    /**
     * 过滤器核心逻辑：提取并校验 token，解析用户信息后写入安全上下文
     *
     * <p>P2 SEC-3 新增：token 签名校验通过后，再调用 {@link SessionService#touchSession}
     * 校验是否被拉黑 / 会话是否仍存在 / 是否空闲超时，任一不通过则清空 SecurityContext（等同未登录）。</p>
     *
     * @param request     HTTP 请求
     * @param response    HTTP 响应
     * @param filterChain 过滤器链
     * @throws ServletException Servlet 异常
     * @throws IOException      IO 异常
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // 1. 提取 token
        String token = extractToken(request);

        // 2. 校验 token 签名 + 过期
        if (StringUtils.hasText(token) && jwtUtil.validateToken(token)) {
            try {
                // 2.1 P2 SEC-3：校验会话状态（黑名单 / 会话集合 / 空闲超时）
                if (!sessionService.touchSession(token)) {
                    // 会话已失效（登出 / 踢人 / 空闲超时），清空 SecurityContext 走未登录流程
                    log.debug("会话校验未通过，token 已失效");
                    SecurityContextHolder.clearContext();
                    filterChain.doFilter(request, response);
                    return;
                }

                // 3. 解析 userId 和 username
                String userId = jwtUtil.getUserIdFromToken(token);
                String username = jwtUtil.getUsernameFromToken(token);

                // 4. 解析 orgId，暂存到 request attribute 供 TenantInterceptor 读取
                String orgId = jwtUtil.getOrgIdFromToken(token);
                if (StringUtils.hasText(orgId)) {
                    request.setAttribute(TenantConstant.ORG_ID_REQUEST_ATTR, orgId);
                }

                // 5. 暂存 userId 到 request attribute，供 Controller 读取（M2.3 任务创建需要）
                if (StringUtils.hasText(userId)) {
                    request.setAttribute(AgentConstant.USER_ID_REQUEST_ATTR, userId);
                    // 5.1 M7.6 三维度 RBAC：同时存入 tenant attribute，供 TenantInterceptor 注入 TenantContext
                    request.setAttribute(TenantConstant.USER_ID_REQUEST_ATTR, userId);
                }

                // 6. 设置 SecurityContext
                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        username,
                        null,
                        Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
                );
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authToken);
            } catch (Exception e) {
                // 解析失败时清空安全上下文
                log.warn("JWT token 解析失败：{}", e.getMessage());
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 从请求头中提取 Bearer token
     *
     * @param request HTTP 请求
     * @return token 字符串，不存在时返回 null
     */
    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}