package com.finrpa.auth.aspect;

import com.finrpa.auth.annotation.RequirePermission;
import com.finrpa.auth.service.PermissionService;
import com.finrpa.auth.util.JwtUtil;
import com.finrpa.common.exception.BusinessException;
import com.finrpa.common.response.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;

/**
 * 权限校验 AOP 切面，拦截带 {@link RequirePermission} 注解的方法
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Aspect
@Component
@RequiredArgsConstructor
public class PermissionAspect {

    /** 权限服务 */
    private final PermissionService permissionService;
    /** JWT 工具 */
    private final JwtUtil jwtUtil;

    /**
     * 环绕通知：校验当前请求用户是否具备指定资源与操作的权限
     *
     * @param joinPoint         连接点
     * @param requirePermission 权限注解
     * @return 目标方法执行结果
     * @throws Throwable 目标方法抛出的异常
     */
    @Around("@annotation(requirePermission)")
    public Object checkPermission(ProceedingJoinPoint joinPoint, RequirePermission requirePermission) throws Throwable {
        // 1. 提取 token
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        String token = extractToken(request);

        // 2. 校验 token
        if (token == null || !jwtUtil.validateToken(token)) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "未登录或token已过期");
        }

        // 3. 解析 userId 并获取资源类型与操作
        String userId = jwtUtil.getUserIdFromToken(token);
        String resourceType = requirePermission.resourceType();
        String action = requirePermission.action();

        // 4. 提取 resourceId
        String resourceId = extractResourceId(joinPoint);

        // 5. 权限检查
        boolean hasPermission = permissionService.checkPermission(userId, resourceType, resourceId, action);
        if (!hasPermission) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限访问");
        }

        return joinPoint.proceed();
    }

    /**
     * 从请求头中提取 Bearer token
     *
     * @param request HTTP 请求
     * @return token 字符串，不存在时返回 null
     */
    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    /**
     * 从连接点参数中提取资源 ID（参数名为 id 或 resourceId）
     *
     * @param joinPoint 连接点
     * @return 资源 ID，不存在时返回 null
     */
    private String extractResourceId(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] paramNames = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();

        for (int i = 0; i < paramNames.length; i++) {
            if ("id".equals(paramNames[i]) || "resourceId".equals(paramNames[i])) {
                return args[i] != null ? args[i].toString() : null;
            }
        }

        return null;
    }
}