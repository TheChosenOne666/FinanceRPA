package com.finrpa.notification.channels;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finrpa.notification.config.NotificationProperties;
import com.finrpa.notification.constant.NotificationConstant;
import com.finrpa.notification.dto.NotificationMessage;
import com.finrpa.notification.dto.response.NotificationSendResultVO;
import com.finrpa.notification.enums.NotificationChannelEnum;
import com.finrpa.notification.enums.NotificationTemplateEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 企业微信通道单元测试（M6.6）
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
class WeComChannelTest {

    private NotificationProperties properties;
    private WeComChannel weComChannel;

    @BeforeEach
    void setUp() {
        properties = new NotificationProperties();
        weComChannel = new WeComChannel();
        ReflectionTestUtils.setField(weComChannel, "properties", properties);
        ReflectionTestUtils.setField(weComChannel, "objectMapper", new ObjectMapper());
    }

    @Test
    @DisplayName("getChannel - 返回企业微信枚举")
    void getChannel_ReturnsWecomEnum() {
        assertEquals(NotificationChannelEnum.WECOM, weComChannel.getChannel());
    }

    @Test
    @DisplayName("isConfigured - webhookUrl 为空返回 false")
    void isConfigured_EmptyUrl_ReturnsFalse() {
        properties.getWecom().setWebhookUrl("");
        assertFalse(weComChannel.isConfigured());
    }

    @Test
    @DisplayName("isConfigured - webhookUrl 非空返回 true")
    void isConfigured_NonEmptyUrl_ReturnsTrue() {
        properties.getWecom().setWebhookUrl("https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=test");
        assertTrue(weComChannel.isConfigured());
    }

    @Test
    @DisplayName("send - 未配置 Webhook 返回失败结果")
    void send_NotConfigured_ReturnsFailure() {
        // arrange
        properties.getWecom().setWebhookUrl("");
        NotificationMessage message = NotificationMessage.builder()
                .template(NotificationTemplateEnum.APPROVAL_PENDING)
                .title("审批待处理通知")
                .content("正文")
                .build();

        // act
        NotificationSendResultVO result = weComChannel.send(message);

        // assert
        assertFalse(result.getSuccess());
        assertTrue(result.getErrorMessage().contains("未配置"));
    }

    @Test
    @DisplayName("send - 响应 errcode=0 返回成功结果")
    void send_SuccessResponse_ReturnsSuccess() {
        // arrange
        properties.getWecom().setWebhookUrl("https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=test");
        WebClient mockWebClient = WebClient.builder()
                .exchangeFunction(request -> Mono.just(ClientResponse
                        .create(HttpStatus.OK)
                        .header("Content-Type", "application/json")
                        .body("{\"errcode\":0,\"errmsg\":\"ok\"}")
                        .build()))
                .build();
        ReflectionTestUtils.setField(weComChannel, "notificationWebClient", mockWebClient);

        NotificationMessage message = NotificationMessage.builder()
                .template(NotificationTemplateEnum.APPROVAL_PENDING)
                .title("审批待处理通知")
                .content("审批正文")
                .params(Map.of())
                .build();

        // act
        NotificationSendResultVO result = weComChannel.send(message);

        // assert
        assertTrue(result.getSuccess());
        assertEquals(NotificationConstant.CHANNEL_WECOM, result.getChannel());
        assertTrue(result.getRawResponse().contains("\"errcode\":0"));
    }

    @Test
    @DisplayName("send - 响应 errcode!=0 返回失败结果")
    void send_ErrorResponse_ReturnsFailure() {
        // arrange
        properties.getWecom().setWebhookUrl("https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=test");
        WebClient mockWebClient = WebClient.builder()
                .exchangeFunction(request -> Mono.just(ClientResponse
                        .create(HttpStatus.OK)
                        .header("Content-Type", "application/json")
                        .body("{\"errcode\":93000,\"errmsg\":\"invalid webhook url\"}")
                        .build()))
                .build();
        ReflectionTestUtils.setField(weComChannel, "notificationWebClient", mockWebClient);

        NotificationMessage message = NotificationMessage.builder()
                .template(NotificationTemplateEnum.APPROVAL_PENDING)
                .title("审批待处理通知")
                .content("正文")
                .build();

        // act
        NotificationSendResultVO result = weComChannel.send(message);

        // assert
        assertFalse(result.getSuccess());
        assertTrue(result.getErrorMessage().contains("企业微信返回错误"));
    }
}
