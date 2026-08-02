package com.finrpa.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.redis.spring.RedisLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/**
 * ShedLock 分布式定时任务锁配置（M6.4）
 *
 * <p>基于 Redis 实现分布式锁，确保集群部署下同一时刻仅单节点执行定时任务。
 * M6.4 审批超时检测调度器通过 {@code @SchedulerLock} 注解获取锁，避免多节点重复扫描。</p>
 *
 * <p>依赖：
 * <ul>
 *   <li>{@code shedlock-spring}：提供 {@link EnableSchedulerLock} + {@code @SchedulerLock} AOP</li>
 *   <li>{@code shedlock-provider-redis-spring}：提供 {@link RedisLockProvider}（基于 Redis SET NX PX）</li>
 * </ul>
 * </p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT30S")
public class ShedLockConfig {

    /** ShedLock 锁 key 前缀（避免与其他业务 Redis key 冲突） */
    private static final String LOCK_KEY_PREFIX = "finrpa:shedlock";

    /**
     * 构建 Redis 锁提供者
     *
     * <p>使用 {@link RedisLockProvider} 基于 Redis 的 {@code SET key value NX PX} 命令实现分布式锁。
     * 锁 key 统一加 {@code finrpa:shedlock} 前缀。</p>
     *
     * @param connectionFactory Redis 连接工厂（由 Spring Data Redis 自动配置注入）
     * @return LockProvider 锁提供者实例
     */
    @Bean
    public LockProvider lockProvider(RedisConnectionFactory connectionFactory) {
        return new RedisLockProvider(connectionFactory, LOCK_KEY_PREFIX);
    }
}
