package com.finrpa.auth.annotation;

import java.lang.annotation.*;

/**
 * 权限校验注解，标注在方法上由 {@link com.finrpa.auth.aspect.PermissionAspect} 拦截校验
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequirePermission {

    /** 资源类型 */
    String resourceType();

    /** 操作类型（read/view/create/update/delete/approve） */
    String action();
}