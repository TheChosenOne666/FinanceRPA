package com.finrpa.system.config;

import com.finrpa.system.interceptor.MaintenanceInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置：注册维护模式拦截器（P3 OPS-3）
 *
 * <p>注册 {@link MaintenanceInterceptor} 拦截所有 {@code /api/**} 请求，
 * 执行顺序设为最高优先级（最先执行），确保维护期间尽早拦截。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Configuration
@RequiredArgsConstructor
public class MaintenanceWebMvcConfig implements WebMvcConfigurer {

    /** 维护模式拦截器 */
    private final MaintenanceInterceptor maintenanceInterceptor;

    /**
     * 注册拦截器
     *
     * @param registry 拦截器注册表
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 1. 注册 MaintenanceInterceptor，拦截所有 API 路径，最高优先级
        registry.addInterceptor(maintenanceInterceptor)
                .addPathPatterns("/api/**")
                .order(Integer.MIN_VALUE);
    }
}
