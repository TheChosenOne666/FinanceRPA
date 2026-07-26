package com.finrpa.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 权限校验注解
 *
 * <p>M1.1 将由 {@code PermissionInterceptor} 基于 JWT 解析当前用户角色并校验。</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuthCheck {

    /**
     * 必须具备的角色标识（对齐系统设计 6.1.1 角色枚举：super_admin / org_admin / operator / approver / viewer）
     */
    String mustRole() default "";
}
