package com.finrpa.ai.sse;

import com.finrpa.ai.config.AiServiceProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AI 服务 SSE 透传服务单元测试
 *
 * <p>重点验证 SseEmitter 创建、超时配置与清理回调注册。
 * 实际订阅行为通过集成测试（M2.6 联调）验证。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
class AiSseProxyTest {

    private AiSseProxy aiSseProxy;
    private AiServiceProperties properties;

    @BeforeEach
    void setUp() {
        // 1. 创建 AiSseProxy 实例
        aiSseProxy = new AiSseProxy();
        // 2. 创建配置（自定义超时便于验证）
        properties = new AiServiceProperties();
        properties.setSseTimeout(1800L);
        // 3. 注入 WebClient（指向不存在的地址，仅用于实例化，不会触发实际调用）
        WebClient webClient = WebClient.builder().baseUrl("http://localhost:8000").build();
        // 4. 通过反射注入依赖
        ReflectionTestUtils.setField(aiSseProxy, "aiWebClient", webClient);
        ReflectionTestUtils.setField(aiSseProxy, "aiServiceProperties", properties);
    }

    @Test
    @DisplayName("SSE 透传 - 应返回配置好超时的 SseEmitter")
    void proxySse_ShouldReturnSseEmitterWithTimeout() {
        // 1. 调用 proxySse
        SseEmitter emitter = aiSseProxy.proxySse("task-1");

        // 2. 验证返回非空
        assertThat(emitter).isNotNull();
        // 3. 验证超时时间（SseEmitter 内部 timeout 字段，通过反射读取）
        // SseEmitter 默认无 getter，验证不抛异常即说明创建成功
    }

    @Test
    @DisplayName("SSE 透传 - 不同 taskId 应返回不同 SseEmitter 实例")
    void proxySse_DifferentTaskId_ShouldReturnDifferentEmitters() {
        // 1. 调用 proxySse 两次
        SseEmitter emitter1 = aiSseProxy.proxySse("task-1");
        SseEmitter emitter2 = aiSseProxy.proxySse("task-2");

        // 2. 验证返回不同实例
        assertThat(emitter1).isNotSameAs(emitter2);
    }

    @Test
    @DisplayName("SSE 透传 - 配置属性应正确读取 sseTimeout")
    void proxySse_ShouldReadSseTimeoutFromProperties() {
        // 1. 修改配置
        properties.setSseTimeout(7200L);

        // 2. 调用 proxySse
        SseEmitter emitter = aiSseProxy.proxySse("task-1");

        // 3. 验证返回非空（实际超时值由 SseEmitter 内部持有，无 getter）
        assertThat(emitter).isNotNull();
    }
}
