package com.finrpa.tenant.interceptor;

import com.finrpa.tenant.constant.TenantConstant;
import com.finrpa.tenant.context.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

/**
 * 租户上下文拦截器
 *
 * <p>从 request attribute 读取 JwtAuthenticationFilter 预存的 orgId，设置到 {@link TenantContext}，
 * 在请求结束时清理 ThreadLocal 防止线程复用污染。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Slf4j
@Component
public class TenantInterceptor implements HandlerInterceptor {

    /**
     * 请求处理前：从 request attribute 读取 orgId 注入 TenantContext
     *
     * @param request  HTTP 请求
     * @param response HTTP 响应
     * @param handler  处理器
     * @return true-继续后续拦截器与处理器；false-中断请求
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 1. 从 request attribute 读取 orgId（由 JwtAuthenticationFilter 预存）
        Object orgIdObj = request.getAttribute(TenantConstant.ORG_ID_REQUEST_ATTR);
        if (orgIdObj instanceof String orgId && !orgId.isEmpty()) {
            // 2. 设置到 TenantContext，供 MyBatis-Plus 读取
            TenantContext.setOrgId(orgId);
        } else {
            // 3. 未携带 orgId 时记录调试日志（如登录、健康检查等放行接口）
            log.debug("当前请求未携带 orgId，TenantContext 未设置");
        }

        // 4. M7.6 三维度 RBAC：从 request attribute 读取 userId 注入 TenantContext
        Object userIdObj = request.getAttribute(TenantConstant.USER_ID_REQUEST_ATTR);
        if (userIdObj instanceof String userId && !userId.isEmpty()) {
            TenantContext.setUserId(userId);
        }
        return true;
    }

    /**
     * 处理器执行完成后：无操作（保留为后续扩展）
     *
     * @param request      HTTP 请求
     * @param response     HTTP 响应
     * @param handler      处理器
     * @param modelAndView 模型视图
     */
    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) {
        // 无需处理
    }

    /**
     * 请求结束时：清理 TenantContext，防止线程复用导致数据污染
     *
     * @param request  HTTP 请求
     * @param response HTTP 响应
     * @param handler  处理器
     * @param ex       异常（若有）
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        TenantContext.clear();
    }
}
