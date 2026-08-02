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

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * 钉钉群机器人通道（M6.6）
 *
 * <p>调用钉钉群机器人 Webhook，请求体格式：
 * <pre>{@code
 * {
 *   "msgtype": "markdown",
 *   "markdown": { "title": "...", "text": "..." }
 * }
 * }</pre>
 * 响应示例：{@code {"errcode":0,"errmsg":"ok","msgid":"..."}}。</p>
 *
 * <p>加签模式：当 {@code secret} 配置非空时启用，按钉钉文档计算签名拼接 URL：
 * {@code &timestamp=<millis>&sign=<URLEncode(Base64(HmacSHA256(timestamp+"\n"+secret, secret)))>}。</p>
 *
 * <p>常见错误码：
 * <ul>
 *   <li>310000：关键词不匹配</li>
 *   <li>310001：签名错误</li>
 *   <li>310002：IP 不在白名单</li>
 * </ul>
 * </p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Slf4j
@Component
public class DingTalkChannel implements NotificationChannel {

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
     * @return 钉钉通道枚举
     */
    @Override
    public NotificationChannelEnum getChannel() {
        return NotificationChannelEnum.DINGTALK;
    }

    /**
     * 通道是否已配置
     *
     * @return true=Webhook URL 非空 / false=未配置
     */
    @Override
    public boolean isConfigured() {
        String url = properties.getDingtalk().getWebhookUrl();
        return url != null && !url.isBlank();
    }

    /**
     * 发送通知到钉钉群机器人
     *
     * @param message 通知消息（title 作为 markdown.title，content 作为 markdown.text）
     * @return 发送结果
     */
    @Override
    public NotificationSendResultVO send(NotificationMessage message) {
        // 1. 校验配置
        String webhookUrl = properties.getDingtalk().getWebhookUrl();
        if (!isConfigured()) {
            log.warn("钉钉通道未配置 webhookUrl，跳过发送: template={}", message.getTemplate());
            return NotificationSendResultVO.failure(NotificationConstant.CHANNEL_DINGTALK, "钉钉 Webhook 未配置");
        }

        try {
            // 2. 加签（secret 非空时启用）
            String finalUrl = webhookUrl;
            String secret = properties.getDingtalk().getSecret();
            if (secret != null && !secret.isBlank()) {
                finalUrl = buildSignedUrl(webhookUrl, secret);
            }

            // 3. 构建请求体：{"msgtype":"markdown","markdown":{"title":"...","text":"..."}}
            Map<String, Object> markdown = new HashMap<>();
            markdown.put("title", message.getTitle());
            markdown.put("text", message.getContent());
            Map<String, Object> body = new HashMap<>();
            body.put("msgtype", "markdown");
            body.put("markdown", markdown);

            String requestBody = objectMapper.writeValueAsString(body);
            log.debug("钉钉通知请求: url={}, body={}", finalUrl, requestBody);

            // 4. 同步 POST 调用 Webhook，阻塞获取响应
            String response = notificationWebClient.post()
                    .uri(finalUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(BodyInserters.fromValue(requestBody))
                    .retrieve()
                    .bodyToMono(String.class)
                    .onErrorResume(e -> Mono.just("ERROR: " + e.getMessage()))
                    .block();

            // 5. 解析响应（errcode=0 视为成功）
            if (response != null && response.contains("\"errcode\":0")) {
                log.info("钉钉通知发送成功: template={}", message.getTemplate());
                return NotificationSendResultVO.success(NotificationConstant.CHANNEL_DINGTALK, response);
            }
            log.error("钉钉通知发送失败: template={}, response={}", message.getTemplate(), response);
            return NotificationSendResultVO.failure(NotificationConstant.CHANNEL_DINGTALK,
                    "钉钉返回错误: " + response);
        } catch (Exception e) {
            log.error("钉钉通知发送异常: template={}, error={}", message.getTemplate(), e.getMessage(), e);
            return NotificationSendResultVO.failure(NotificationConstant.CHANNEL_DINGTALK,
                    "发送异常: " + e.getMessage());
        }
    }

    /**
     * 构建钉钉加签 URL
     *
     * <p>加签算法：{@code sign = URLEncode(Base64(HmacSHA256(timestamp + "\n" + secret, secret)))}，
     * 拼接到原 Webhook URL 后。</p>
     *
     * @param webhookUrl 原 Webhook URL（已含 access_token 参数）
     * @param secret     加签密钥
     * @return 拼接 timestamp + sign 后的 URL
     * @throws Exception 加签计算异常
     */
    private String buildSignedUrl(String webhookUrl, String secret) throws Exception {
        // 1. 当前毫秒时间戳
        long timestamp = System.currentTimeMillis();

        // 2. 拼接待签名字符串：timestamp + "\n" + secret
        String stringToSign = timestamp + "\n" + secret;

        // 3. HmacSHA256 计算 MAC
        Mac mac = Mac.getInstance(NotificationConstant.DINGTALK_SIGN_ALGORITHM);
        mac.init(new SecretKeySpec(secret.getBytes(NotificationConstant.DINGTALK_CHARSET),
                NotificationConstant.DINGTALK_SIGN_ALGORITHM));
        byte[] signData = mac.doFinal(stringToSign.getBytes(NotificationConstant.DINGTALK_CHARSET));

        // 4. Base64 编码 + URL 编码
        String sign = URLEncoder.encode(Base64.getEncoder().encodeToString(signData), StandardCharsets.UTF_8);

        // 5. 拼接到原 URL
        String separator = webhookUrl.contains("?") ? "&" : "?";
        return webhookUrl + separator + "timestamp=" + timestamp + "&sign=" + sign;
    }
}
