package com.finrpa.system.interceptor;

import com.finrpa.system.service.SystemConfigService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;

/**
 * 维护模式拦截器（P3 OPS-3）
 *
 * <p>读取 sys_config 表的 {@code maintenance.enabled} 开关：
 * <ul>
 *   <li>启用（true）：拦截所有非白名单请求，返回 503 + JSON 错误响应</li>
 *   <li>禁用（false）：放行所有请求</li>
 * </ul>
 * </p>
 *
 * <p>白名单路径（维护期间仍可访问）：
 * 登录 / 刷新 token / 系统配置查询与刷新 / 系统健康检查 / Spring 错误页 / 静态资源。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Slf4j
@Component
public class MaintenanceInterceptor implements HandlerInterceptor {

    /** 系统配置服务 */
    @Resource
    private SystemConfigService systemConfigService;

    /** 白名单路径前缀（维护期间放行） */
    private static final Set<String> WHITELIST_PREFIXES = Set.of(
            "/api/auth/login",
            "/api/auth/refresh",
            "/api/system-config",
            "/api/system-health",
            "/error",
            "/assets",
            "/favicon.ico"
    );

    /**
     * 请求前置处理：维护模式开启时拦截非白名单请求
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1. 读取维护模式开关（带 30s 缓存）
        boolean maintenance = systemConfigService.getBoolean("maintenance.enabled", false);
        if (!maintenance) {
            return true;
        }

        // 2. 白名单放行
        String path = request.getRequestURI();
        for (String prefix : WHITELIST_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }

        // 3. 返回 503 + JSON 错误响应
        response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":503,\"data\":null,\"message\":\"系统维护中，请稍后再试\"}");
        log.warn("[MaintenanceInterceptor] 维护模式拦截请求: path={}", path);
        return false;
    }
}
