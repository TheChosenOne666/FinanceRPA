package com.finrpa.notification.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.util.concurrent.TimeUnit;

/**
 * 通知模块 WebClient 配置（M6.6）
 *
 * <p>构建调用企业微信 / 钉钉 Webhook 的 {@link WebClient}。
 * Webhook 调用为短连接 POST，超时策略：连接 5s / 读写 10s（避免机器人侧响应慢阻塞业务线程）。</p>
 *
 * <p>与 {@code aiWebClient} 隔离，避免通知发送失败影响 AI 服务调用链。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Configuration
public class NotificationWebClientConfig {

    /** 通道 Webhook 连接超时（毫秒） */
    private static final int CONNECT_TIMEOUT_MILLIS = 5_000;

    /** 通道 Webhook 读写超时（秒） */
    private static final int READ_WRITE_TIMEOUT_SECONDS = 10;

    /**
     * 通知通道 WebClient（用于调用企业微信 / 钉钉 Webhook）
     *
     * <p>不设置 baseUrl，每次请求时由通道实现根据 Webhook URL 自行拼接。
     * 默认 Header：Content-Type=application/json、Accept=application/json。</p>
     *
     * @return 配置好的 WebClient
     */
    @Bean("notificationWebClient")
    public WebClient notificationWebClient() {
        // 1. 构建 Netty HttpClient，设置连接与读写超时
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, CONNECT_TIMEOUT_MILLIS)
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(READ_WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(READ_WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)));

        // 2. 构建 WebClient（不设 baseUrl，由调用方按 Webhook URL 自行拼装）
        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(1 * 1024 * 1024))
                .build();
    }
}
