package com.finrpa.ai.config;

import com.finrpa.system.service.SystemConfigService;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * AI 服务配置属性
 *
 * <p>映射 {@code application.yml} 中 {@code ai.*} 配置项，
 * 用于 Python AI 服务调用（WebClient + HTTP Interface）。</p>
 *
 * <p>P3 INT-1：增加 {@link #refreshFromConfig(SystemConfigService)} 方法，从 sys_config 表
 * 读取最新配置并更新字段值。高频字段（retry.maxAttempts 等）刷新后通过 getter 立即热生效；
 * 连接参数（baseUrl / timeout）刷新后因 WebClient 为启动时构建的单例，需重启或调重建接口生效。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "ai")
public class AiServiceProperties {

    /** Python finance-ai 服务基础地址 */
    private String baseUrl = "http://localhost:8000";

    /** 服务间共享密钥（X-Internal-Token Header） */
    private String internalToken = "finrpa-internal-secret";

    /** 连接超时（秒） */
    private int connectTimeout = 5;

    /** 读取超时（秒）—— 同步调用场景 */
    private int readTimeout = 60;

    /** SSE 长连接超时（秒）—— 默认 1 小时 */
    private long sseTimeout = 3600L;

    /** 重试配置 */
    private Retry retry = new Retry();

    /**
     * 重试配置
     */
    @Data
    public static class Retry {
        /** 最大重试次数（含首次调用） */
        private int maxAttempts = 3;
        /** 初始退避（毫秒），指数递增 */
        private long backoff = 1000L;
    }

    /**
     * 从系统配置服务刷新字段值（P3 INT-1）
     *
     * <p>DB 配置缺失时保留 yml 绑定的当前值。高频字段（retry.maxAttempts）刷新后立即热生效；
     * 连接参数（baseUrl/timeout）刷新后需重启或调 WebClient 重建接口生效。</p>
     *
     * @param configService 系统配置服务
     */
    public void refreshFromConfig(SystemConfigService configService) {
        // 1. 连接参数（改动需重启重建 WebClient）
        String dbBaseUrl = configService.getString("ai.base_url", null);
        if (dbBaseUrl != null) {
            this.baseUrl = dbBaseUrl;
        }
        String dbToken = configService.getString("ai.internal_token", null);
        if (dbToken != null) {
            this.internalToken = dbToken;
        }
        Integer dbConnectTimeout = configService.getInteger("ai.connect_timeout", null);
        if (dbConnectTimeout != null) {
            this.connectTimeout = dbConnectTimeout;
        }
        Integer dbReadTimeout = configService.getInteger("ai.read_timeout", null);
        if (dbReadTimeout != null) {
            this.readTimeout = dbReadTimeout;
        }
        Integer dbSseTimeout = configService.getInteger("ai.sse_timeout", null);
        if (dbSseTimeout != null) {
            this.sseTimeout = dbSseTimeout;
        }
        // 2. 重试参数（高频读，刷新后立即热生效）
        Integer dbMaxAttempts = configService.getInteger("ai.retry.max_attempts", null);
        if (dbMaxAttempts != null) {
            this.retry.maxAttempts = dbMaxAttempts;
        }
        Integer dbBackoff = configService.getInteger("ai.retry.backoff", null);
        if (dbBackoff != null) {
            this.retry.backoff = dbBackoff;
        }
    }
}
