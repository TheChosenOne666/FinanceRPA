package com.finrpa.notification.channels;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finrpa.notification.config.NotificationProperties;
import com.finrpa.notification.constant.NotificationConstant;
import com.finrpa.notification.dto.NotificationMessage;
import com.finrpa.notification.dto.response.NotificationSendResultVO;
import com.finrpa.notification.enums.NotificationChannelEnum;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * 企业微信群机器人通道（M6.6）
 *
 * <p>调用企业微信群机器人 Webhook，请求体格式：
 * <pre>{@code
 * {
 *   "msgtype": "markdown",
 *   "markdown": { "content": "..." }
 * }
 * }</pre>
 * 响应示例：{@code {"errcode":0,"errmsg":"ok"}}。</p>
 *
 * <p>Webhook URL 从配置文件读取，为空视为未配置通道。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Slf4j
@Component
public class WeComChannel implements NotificationChannel {

    /** 通知模块配置 */
    @Resource
    private NotificationProperties properties;

    /** 通知通道 WebClient */
    @Resource
    @Qualifier("notificationWebClient")
    private WebClient notificationWebClient;

    /** JSON 序列化工具 */
    @Resource
    private ObjectMapper objectMapper;

    /**
     * 获取通道枚举
     *
     * @return 企业微信通道枚举
     */
    @Override
    public NotificationChannelEnum getChannel() {
        return NotificationChannelEnum.WECOM;
    }

    /**
     * 通道是否已配置
     *
     * @return true=Webhook URL 非空 / false=未配置
     */
    @Override
    public boolean isConfigured() {
        String url = properties.getWecom().getWebhookUrl();
        return url != null && !url.isBlank();
    }

    /**
     * 发送通知到企业微信群机器人
     *
     * @param message 通知消息（仅取 content 字段作为 markdown 正文）
     * @return 发送结果
     */
    @Override
    public NotificationSendResultVO send(NotificationMessage message) {
        // 1. 校验配置
        String webhookUrl = properties.getWecom().getWebhookUrl();
        if (!isConfigured()) {
            log.warn("企业微信通道未配置 webhookUrl，跳过发送: template={}", message.getTemplate());
            return NotificationSendResultVO.failure(NotificationConstant.CHANNEL_WECOM, "企业微信 Webhook 未配置");
        }

        try {
            // 2. 构建请求体：{"msgtype":"markdown","markdown":{"content":"..."}}
            Map<String, Object> markdown = new HashMap<>();
            markdown.put("content", message.getContent());
            Map<String, Object> body = new HashMap<>();
            body.put("msgtype", "markdown");
            body.put("markdown", markdown);

            String requestBody = objectMapper.writeValueAsString(body);
            log.debug("企业微信通知请求: webhookUrl={}, body={}", webhookUrl, requestBody);

            // 3. 同步 POST 调用 Webhook，阻塞获取响应
            String response = notificationWebClient.post()
                    .uri(webhookUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(BodyInserters.fromValue(requestBody))
                    .retrieve()
                    .bodyToMono(String.class)
                    .onErrorResume(e -> Mono.just("ERROR: " + e.getMessage()))
                    .block();

            // 4. 解析响应（errcode=0 视为成功）
            if (response != null && response.contains("\"errcode\":0")) {
                log.info("企业微信通知发送成功: template={}", message.getTemplate());
                return NotificationSendResultVO.success(NotificationConstant.CHANNEL_WECOM, response);
            }
            log.error("企业微信通知发送失败: template={}, response={}", message.getTemplate(), response);
            return NotificationSendResultVO.failure(NotificationConstant.CHANNEL_WECOM,
                    "企业微信返回错误: " + response);
        } catch (Exception e) {
            log.error("企业微信通知发送异常: template={}, error={}", message.getTemplate(), e.getMessage(), e);
            return NotificationSendResultVO.failure(NotificationConstant.CHANNEL_WECOM,
                    "发送异常: " + e.getMessage());
        }
    }
}
