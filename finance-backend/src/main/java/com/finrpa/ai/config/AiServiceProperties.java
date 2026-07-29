package com.finrpa.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * AI 服务配置属性
 *
 * <p>映射 {@code application.yml} 中 {@code ai.*} 配置项，
 * 用于 Python AI 服务调用（WebClient + HTTP Interface）。</p>
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
}
