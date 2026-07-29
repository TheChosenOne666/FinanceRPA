package com.finrpa.ai.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AI 服务配置属性单元测试
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
class AiServicePropertiesTest {

    @Test
    @DisplayName("默认配置 - 应使用合理默认值")
    void defaultValues_ShouldUseReasonableDefaults() {
        // 1. 创建配置实例
        AiServiceProperties properties = new AiServiceProperties();

        // 2. 验证默认值
        assertThat(properties.getBaseUrl()).isEqualTo("http://localhost:8000");
        assertThat(properties.getInternalToken()).isEqualTo("finrpa-internal-secret");
        assertThat(properties.getConnectTimeout()).isEqualTo(5);
        assertThat(properties.getReadTimeout()).isEqualTo(60);
        assertThat(properties.getSseTimeout()).isEqualTo(3600L);
        assertThat(properties.getRetry()).isNotNull();
        assertThat(properties.getRetry().getMaxAttempts()).isEqualTo(3);
        assertThat(properties.getRetry().getBackoff()).isEqualTo(1000L);
    }

    @Test
    @DisplayName("Setter 注入 - 应正确设置属性值")
    void setters_ShouldSetPropertiesCorrectly() {
        // 1. 创建配置实例并设置自定义值
        AiServiceProperties properties = new AiServiceProperties();
        properties.setBaseUrl("http://finance-ai:8000");
        properties.setInternalToken("custom-token");
        properties.setConnectTimeout(10);
        properties.setReadTimeout(120);
        properties.setSseTimeout(7200L);

        AiServiceProperties.Retry retry = new AiServiceProperties.Retry();
        retry.setMaxAttempts(5);
        retry.setBackoff(2000L);
        properties.setRetry(retry);

        // 2. 验证设置值
        assertThat(properties.getBaseUrl()).isEqualTo("http://finance-ai:8000");
        assertThat(properties.getInternalToken()).isEqualTo("custom-token");
        assertThat(properties.getConnectTimeout()).isEqualTo(10);
        assertThat(properties.getReadTimeout()).isEqualTo(120);
        assertThat(properties.getSseTimeout()).isEqualTo(7200L);
        assertThat(properties.getRetry().getMaxAttempts()).isEqualTo(5);
        assertThat(properties.getRetry().getBackoff()).isEqualTo(2000L);
    }

    @Test
    @DisplayName("Retry 默认配置 - 应使用合理默认值")
    void retryDefaultValues_ShouldUseReasonableDefaults() {
        // 1. 创建 Retry 配置
        AiServiceProperties.Retry retry = new AiServiceProperties.Retry();

        // 2. 验证默认值
        assertThat(retry.getMaxAttempts()).isEqualTo(3);
        assertThat(retry.getBackoff()).isEqualTo(1000L);
    }
}
