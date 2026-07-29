package com.finrpa.ai.sse;

import com.finrpa.ai.config.AiServiceProperties;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.time.Duration;

/**
 * AI 服务 SSE 透传服务
 *
 * <p>从 Python AI 服务订阅 SSE 事件流（{@code GET /api/v1/ai/sse/tasks/{taskId}}），
 * 通过 Spring MVC {@link org.springframework.web.servlet.mvc.method.annotation.SseEmitter}
 * 透传给前端客户端。</p>
 *
 * <p>透传策略：
 * <ul>
 *   <li>订阅 Python SSE 流，每个事件原样转发给前端（保留 event name + data）</li>
 *   <li>Flux 层超时控制：{@code sse-timeout}（默认 1 小时）</li>
 *   <li>Python 不可用时发送 {@code error} 事件并关闭连接</li>
 *   <li>前端断开连接时自动取消订阅，释放资源</li>
 * </ul>
 * </p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Slf4j
@Component
public class AiSseProxy {

    /** SSE 事件流类型引用 */
    private static final ParameterizedTypeReference<ServerSentEvent<String>> SSE_TYPE_REF =
            new ParameterizedTypeReference<>() {};

    /** AI 服务 WebClient（同步调用 WebClient，复用 baseUrl 与 Header 配置） */
    @Resource(name = "aiWebClient")
    private WebClient aiWebClient;

    /** AI 服务配置 */
    @Resource
    private AiServiceProperties aiServiceProperties;

    /**
     * 透传 Python SSE 事件流给前端
     *
     * <p>调用方在 Controller 中将返回的 {@link org.springframework.web.servlet.mvc.method.annotation.SseEmitter}
     * 直接作为响应体返回，Spring MVC 会自动处理异步推送。</p>
     *
     * @param taskId 任务 ID
     * @return 配置好的 SseEmitter
     */
    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter proxySse(String taskId) {
        // 1. 创建 SseEmitter，设置超时时间（毫秒）
        long timeoutMs = aiServiceProperties.getSseTimeout() * 1000L;
        org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter =
                new org.springframework.web.servlet.mvc.method.annotation.SseEmitter(timeoutMs);

        log.info("SSE 透传启动: taskId={}, timeout={}ms", taskId, timeoutMs);

        // 2. 构建 Python SSE 订阅 Flux
        Flux<ServerSentEvent<String>> sseFlux = aiWebClient.get()
                .uri("/api/v1/ai/sse/tasks/{taskId}", taskId)
                .retrieve()
                .bodyToFlux(SSE_TYPE_REF)
                .timeout(Duration.ofSeconds(aiServiceProperties.getSseTimeout()))
                .doOnNext(event -> forwardEvent(emitter, taskId, event))
                .doOnError(e -> handleStreamError(emitter, taskId, e))
                .doOnComplete(() -> {
                    log.info("SSE 流正常结束: taskId={}", taskId);
                    emitter.complete();
                });

        // 3. 订阅 Flux（异步执行）
        Disposable disposable = sseFlux.subscribe();

        // 4. 注册清理回调：前端断开 / 超时 / 异常时取消订阅
        emitter.onCompletion(() -> {
            log.info("SSE 连接关闭，释放订阅: taskId={}", taskId);
            disposable.dispose();
        });
        emitter.onTimeout(() -> {
            log.warn("SSE 连接超时: taskId={}", taskId);
            disposable.dispose();
        });
        emitter.onError(throwable -> {
            log.error("SSE 连接异常: taskId={}", taskId, throwable);
            disposable.dispose();
        });

        return emitter;
    }

    /**
     * 转发单个 SSE 事件给前端
     *
     * @param emitter SseEmitter
     * @param taskId  任务 ID（日志用）
     * @param event   Python 推送的 SSE 事件
     */
    private void forwardEvent(
            org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter,
            String taskId,
            ServerSentEvent<String> event) {
        try {
            // 1. 构建事件：保留 event name 和 data
            org.springframework.web.servlet.mvc.method.annotation.SseEmitter.SseEventBuilder builder =
                    org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event();
            if (event.event() != null) {
                builder.name(event.event());
            }
            if (event.data() != null) {
                builder.data(event.data());
            }
            // 2. 发送事件
            emitter.send(builder);
        } catch (IOException e) {
            log.warn("SSE 事件转发失败（客户端可能已断开）: taskId={}", taskId);
            emitter.completeWithError(e);
        }
    }

    /**
     * 处理 SSE 流异常（Python 不可用 / 超时 / 网络错误）
     *
     * @param emitter   SseEmitter
     * @param taskId    任务 ID
     * @param throwable 异常
     */
    private void handleStreamError(
            org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter,
            String taskId,
            Throwable throwable) {
        log.error("SSE 流异常: taskId={}, error={}", taskId, throwable.getMessage());
        try {
            // 1. 向前端推送 error 事件，便于客户端感知
            emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event()
                    .name("error")
                    .data("{\"error\":\"SSE stream error: " + throwable.getMessage() + "\"}"));
        } catch (IOException ignored) {
            // 客户端已断开，忽略
        }
        emitter.completeWithError(throwable);
    }
}
