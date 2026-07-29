package com.finrpa.ai.config;

import com.finrpa.ai.client.AiServiceClient;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.support.WebClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import reactor.netty.http.client.HttpClient;

import java.util.concurrent.TimeUnit;

/**
 * AI 服务 WebClient 与 HTTP Interface 配置
 *
 * <p>构建调用 Python AI 服务的 {@link WebClient}（携带 X-Internal-Token Header），
 * 并通过 {@link HttpServiceProxyFactory} 创建 {@link AiServiceClient} 声明式客户端代理。</p>
 *
 * <p>超时策略：
 * <ul>
 *   <li>连接超时：{@code connect-timeout}（默认 5s）</li>
 *   <li>响应超时：{@code read-timeout}（默认 60s，用于同步调用）</li>
 *   <li>SSE 长连接超时：在 Flux 层通过 {@code .timeout()} 单独控制（默认 1h）</li>
 * </ul>
 * </p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Configuration
public class AiWebClientConfig {

    /**
     * AI 服务 WebClient（同步调用）
     *
     * <p>携带默认 Header：X-Internal-Token、Content-Type=application/json、Accept=application/json。</p>
     *
     * @param properties AI 服务配置
     * @return 配置好的 WebClient
     */
    @Bean("aiWebClient")
    public WebClient aiWebClient(AiServiceProperties properties) {
        // 1. 构建 Netty HttpClient，设置连接与读写超时
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, properties.getConnectTimeout() * 1000)
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(properties.getReadTimeout(), TimeUnit.SECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(properties.getReadTimeout(), TimeUnit.SECONDS)));

        // 2. 构建 WebClient，设置 baseUrl、默认 Header
        // Python 端 Pydantic 已配置 alias_generator=to_camel，JSON 收发统一驼峰，与 Java camelCase 直接对齐
        return WebClient.builder()
                .baseUrl(properties.getBaseUrl())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("X-Internal-Token", properties.getInternalToken())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();
    }

    /**
     * AI 服务 HTTP Interface 客户端
     *
     * <p>基于 {@link WebClient} 创建 {@link AiServiceClient} 声明式代理。</p>
     *
     * @param aiWebClient AI 服务 WebClient
     * @return AiServiceClient 代理实例
     */
    @Bean
    public AiServiceClient aiServiceClient(WebClient aiWebClient) {
        // 1. 构建 HttpServiceProxyFactory
        HttpServiceProxyFactory proxyFactory = HttpServiceProxyFactory
                .builderFor(WebClientAdapter.create(aiWebClient))
                .build();

        // 2. 创建 AiServiceClient 代理
        return proxyFactory.createClient(AiServiceClient.class);
    }
}
