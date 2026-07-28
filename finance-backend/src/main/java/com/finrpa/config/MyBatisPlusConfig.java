package com.finrpa.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.finrpa.tenant.handler.TenantLineHandlerImpl;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置
 *
 * <p>启用租户隔离插件（{@link TenantLineInnerInterceptor}）与分页插件（{@link PaginationInnerInterceptor}）。
 * 租户插件须在分页插件之前添加，确保 SQL 解析阶段正确追加 {@code WHERE org_id = ?}。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Configuration
public class MyBatisPlusConfig {

    /** 租户行处理器（由 Spring 注入） */
    @Resource
    private TenantLineHandlerImpl tenantLineHandler;

    /**
     * 构建 MyBatis-Plus 拦截器链
     *
     * @return MybatisPlusInterceptor 拦截器实例
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        // 1. 创建拦截器
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 2. 租户隔离插件（必须在分页之前，否则无法追加租户条件到分页 SQL）
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(tenantLineHandler));
        // 3. 分页插件（PostgreSQL 方言）
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.POSTGRE_SQL));
        return interceptor;
    }
}
