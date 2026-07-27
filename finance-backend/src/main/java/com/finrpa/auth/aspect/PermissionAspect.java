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

@Aspect
@Component
@RequiredArgsConstructor
public class PermissionAspect {

    private final PermissionService permissionService;
    private final JwtUtil jwtUtil;

    @Around("@annotation(requirePermission)")
    public Object checkPermission(ProceedingJoinPoint joinPoint, RequirePermission requirePermission) throws Throwable {
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        String token = extractToken(request);

        if (token == null || !jwtUtil.validateToken(token)) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "未登录或token已过期");
        }

        String userId = jwtUtil.getUserIdFromToken(token);
        String resourceType = requirePermission.resourceType();
        String action = requirePermission.action();

        String resourceId = extractResourceId(joinPoint);

        boolean hasPermission = permissionService.checkPermission(userId, resourceType, resourceId, action);
        if (!hasPermission) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限访问");
        }

        return joinPoint.proceed();
    }

    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

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