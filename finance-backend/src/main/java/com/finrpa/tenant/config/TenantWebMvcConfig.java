package com.finrpa.tenant.config;

import com.finrpa.tenant.interceptor.TenantInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置：注册租户拦截器
 *
 * <p>注册 {@link TenantInterceptor} 拦截所有请求路径，由拦截器在请求开始时设置 TenantContext、
 * 请求结束时清理 ThreadLocal。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Configuration
@RequiredArgsConstructor
public class TenantWebMvcConfig implements WebMvcConfigurer {

    /** 租户拦截器 */
    private final TenantInterceptor tenantInterceptor;

    /**
     * 注册拦截器：拦截所有路径
     *
     * @param registry 拦截器注册表
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 1. 注册 TenantInterceptor，拦截所有路径
        registry.addInterceptor(tenantInterceptor)
                .addPathPatterns("/**");
    }
}
