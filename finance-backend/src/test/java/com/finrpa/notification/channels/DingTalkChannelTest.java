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

import java.net.URI;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 钉钉通道单元测试（M6.6）
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
class DingTalkChannelTest {

    private NotificationProperties properties;
    private DingTalkChannel dingTalkChannel;

    @BeforeEach
    void setUp() {
        properties = new NotificationProperties();
        dingTalkChannel = new DingTalkChannel();
        ReflectionTestUtils.setField(dingTalkChannel, "properties", properties);
        ReflectionTestUtils.setField(dingTalkChannel, "objectMapper", new ObjectMapper());
    }

    @Test
    @DisplayName("getChannel - 返回钉钉枚举")
    void getChannel_ReturnsDingTalkEnum() {
        assertEquals(NotificationChannelEnum.DINGTALK, dingTalkChannel.getChannel());
    }

    @Test
    @DisplayName("isConfigured - webhookUrl 为空返回 false")
    void isConfigured_EmptyUrl_ReturnsFalse() {
        properties.getDingtalk().setWebhookUrl("");
        assertFalse(dingTalkChannel.isConfigured());
    }

    @Test
    @DisplayName("isConfigured - webhookUrl 非空返回 true")
    void isConfigured_NonEmptyUrl_ReturnsTrue() {
        properties.getDingtalk().setWebhookUrl("https://oapi.dingtalk.com/robot/send?access_token=test");
        assertTrue(dingTalkChannel.isConfigured());
    }

    @Test
    @DisplayName("send - 未配置 Webhook 返回失败结果")
    void send_NotConfigured_ReturnsFailure() {
        // arrange
        properties.getDingtalk().setWebhookUrl("");
        NotificationMessage message = NotificationMessage.builder()
                .template(NotificationTemplateEnum.APPROVAL_PENDING)
                .title("审批待处理通知")
                .content("正文")
                .build();

        // act
        NotificationSendResultVO result = dingTalkChannel.send(message);

        // assert
        assertFalse(result.getSuccess());
        assertTrue(result.getErrorMessage().contains("未配置"));
    }

    @Test
    @DisplayName("send - 无 secret + 响应 errcode=0 返回成功结果（URL 不加签）")
    void send_NoSecretSuccessResponse_ReturnsSuccess() {
        // arrange
        properties.getDingtalk().setWebhookUrl("https://oapi.dingtalk.com/robot/send?access_token=test");
        properties.getDingtalk().setSecret("");
        WebClient mockWebClient = WebClient.builder()
                .exchangeFunction(request -> {
                    // 校验：URL 未追加 timestamp + sign（未配置 secret）
                    URI uri = request.url();
                    assertFalse(uri.toString().contains("timestamp="),
                            "无 secret 时 URL 不应包含 timestamp");
                    assertFalse(uri.toString().contains("sign="),
                            "无 secret 时 URL 不应包含 sign");
                    return Mono.just(ClientResponse
                            .create(HttpStatus.OK)
                            .header("Content-Type", "application/json")
                            .body("{\"errcode\":0,\"errmsg\":\"ok\",\"msgid\":\"abc\"}")
                            .build());
                })
                .build();
        ReflectionTestUtils.setField(dingTalkChannel, "notificationWebClient", mockWebClient);

        NotificationMessage message = NotificationMessage.builder()
                .template(NotificationTemplateEnum.APPROVAL_PENDING)
                .title("审批待处理通知")
                .content("审批正文")
                .build();

        // act
        NotificationSendResultVO result = dingTalkChannel.send(message);

        // assert
        assertTrue(result.getSuccess());
        assertEquals(NotificationConstant.CHANNEL_DINGTALK, result.getChannel());
    }

    @Test
    @DisplayName("send - 配置 secret 后 URL 追加 timestamp + sign")
    void send_WithSecret_UrlContainsTimestampAndSign() {
        // arrange
        properties.getDingtalk().setWebhookUrl("https://oapi.dingtalk.com/robot/send?access_token=test");
        properties.getDingtalk().setSecret("SEC_TEST_SECRET");
        WebClient mockWebClient = WebClient.builder()
                .exchangeFunction(request -> {
                    URI uri = request.url();
                    assertTrue(uri.toString().contains("timestamp="),
                            "配置 secret 时 URL 应包含 timestamp");
                    assertTrue(uri.toString().contains("sign="),
                            "配置 secret 时 URL 应包含 sign");
                    return Mono.just(ClientResponse
                            .create(HttpStatus.OK)
                            .header("Content-Type", "application/json")
                            .body("{\"errcode\":0,\"errmsg\":\"ok\"}")
                            .build());
                })
                .build();
        ReflectionTestUtils.setField(dingTalkChannel, "notificationWebClient", mockWebClient);

        NotificationMessage message = NotificationMessage.builder()
                .template(NotificationTemplateEnum.TASK_FAILED)
                .title("任务失败告警")
                .content("正文")
                .build();

        // act
        NotificationSendResultVO result = dingTalkChannel.send(message);

        // assert
        assertTrue(result.getSuccess());
    }

    @Test
    @DisplayName("send - 响应 errcode!=0 返回失败结果")
    void send_ErrorResponse_ReturnsFailure() {
        // arrange
        properties.getDingtalk().setWebhookUrl("https://oapi.dingtalk.com/robot/send?access_token=test");
        WebClient mockWebClient = WebClient.builder()
                .exchangeFunction(request -> Mono.just(ClientResponse
                        .create(HttpStatus.OK)
                        .header("Content-Type", "application/json")
                        .body("{\"errcode\":310000,\"errmsg\":\"keywords not in content\"}")
                        .build()))
                .build();
        ReflectionTestUtils.setField(dingTalkChannel, "notificationWebClient", mockWebClient);

        NotificationMessage message = NotificationMessage.builder()
                .template(NotificationTemplateEnum.APPROVAL_PENDING)
                .title("审批待处理通知")
                .content("正文")
                .params(Map.of())
                .build();

        // act
        NotificationSendResultVO result = dingTalkChannel.send(message);

        // assert
        assertFalse(result.getSuccess());
        assertTrue(result.getErrorMessage().contains("钉钉返回错误"));
    }
}
