package com.finrpa.agent.config;

import com.finrpa.agent.interceptor.InternalTokenInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Agent 模块 Web MVC 配置
 *
 * <p>注册 {@link InternalTokenInterceptor} 拦截 {@code /internal/**} 路径，
 * 校验 Python 回调请求的 X-Internal-Token Header。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Configuration
@RequiredArgsConstructor
public class AgentWebMvcConfig implements WebMvcConfigurer {

    /** 内部 API 鉴权拦截器 */
    private final InternalTokenInterceptor internalTokenInterceptor;

    /**
     * 注册拦截器
     *
     * @param registry 拦截器注册表
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 1. 注册 InternalTokenInterceptor，仅拦截 /internal/** 路径
        registry.addInterceptor(internalTokenInterceptor)
                .addPathPatterns("/internal/**");
    }
}
