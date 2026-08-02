package com.finrpa.notification.service.impl;

import com.finrpa.common.exception.BusinessException;
import com.finrpa.notification.channels.NotificationChannel;
import com.finrpa.notification.constant.NotificationConstant;
import com.finrpa.notification.dispatcher.NotificationDispatcher;
import com.finrpa.notification.dto.NotificationMessage;
import com.finrpa.notification.dto.request.NotificationTestRequest;
import com.finrpa.notification.dto.response.ChannelVO;
import com.finrpa.notification.dto.response.NotificationSendResultVO;
import com.finrpa.notification.dto.response.RetryQueueStatsVO;
import com.finrpa.notification.enums.NotificationChannelEnum;
import com.finrpa.notification.enums.NotificationTemplateEnum;
import com.finrpa.notification.service.NotificationAttemptService;
import com.finrpa.notification.templates.NotificationTemplateRenderer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RList;
import org.redisson.api.RedissonClient;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 通知服务实现单元测试（M6.6）
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock
    private NotificationChannel weComChannel;

    @Mock
    private NotificationChannel dingTalkChannel;

    @Mock
    private NotificationTemplateRenderer templateRenderer;

    @Mock
    private NotificationDispatcher dispatcher;

    @Mock
    private NotificationAttemptService attemptService;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RList<String> retryQueue;

    private NotificationServiceImpl notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationServiceImpl();
        // 注入通道 List（两个 mock）
        ReflectionTestUtils.setField(notificationService, "channels", List.of(weComChannel, dingTalkChannel));
        ReflectionTestUtils.setField(notificationService, "templateRenderer", templateRenderer);
        ReflectionTestUtils.setField(notificationService, "dispatcher", dispatcher);
        ReflectionTestUtils.setField(notificationService, "attemptService", attemptService);
        ReflectionTestUtils.setField(notificationService, "redissonClient", redissonClient);

        // 通用 stub：通道枚举返回
        lenient().when(weComChannel.getChannel()).thenReturn(NotificationChannelEnum.WECOM);
        lenient().when(dingTalkChannel.getChannel()).thenReturn(NotificationChannelEnum.DINGTALK);
        lenient().doReturn(retryQueue).when(redissonClient).getList(NotificationConstant.RETRY_QUEUE_KEY);
    }

    // region listChannels

    @Test
    @DisplayName("listChannels - 返回所有通道及配置状态")
    void listChannels_ReturnsAllChannels() {
        // arrange
        when(weComChannel.isConfigured()).thenReturn(true);
        when(dingTalkChannel.isConfigured()).thenReturn(false);

        // act
        List<ChannelVO> result = notificationService.listChannels();

        // assert
        assertEquals(2, result.size());
        ChannelVO weCom = result.stream().filter(c -> "wecom".equals(c.getChannel())).findFirst().orElseThrow();
        assertEquals("企业微信群机器人", weCom.getLabel());
        assertTrue(weCom.getConfigured());
        ChannelVO dingTalk = result.stream().filter(c -> "dingtalk".equals(c.getChannel())).findFirst().orElseThrow();
        assertEquals("钉钉群机器人", dingTalk.getLabel());
        assertFalse(dingTalk.getConfigured());
    }

    // endregion

    // region send

    @Test
    @DisplayName("send - 通道已配置且发送成功返回 success")
    void send_ConfiguredAndSent_ReturnsSuccess() {
        // arrange
        NotificationMessage mockMessage = NotificationMessage.builder()
                .template(NotificationTemplateEnum.APPROVAL_PENDING)
                .title("审批待处理通知")
                .content("正文")
                .build();
        when(templateRenderer.render(eq(NotificationTemplateEnum.APPROVAL_PENDING), any()))
                .thenReturn(mockMessage);
        when(weComChannel.isConfigured()).thenReturn(true);
        when(weComChannel.send(mockMessage))
                .thenReturn(NotificationSendResultVO.success("wecom", "{\"errcode\":0}"));

        // act
        NotificationSendResultVO result = notificationService.send(
                NotificationChannelEnum.WECOM,
                NotificationTemplateEnum.APPROVAL_PENDING,
                Map.of("approvalId", "apr_001"));

        // assert
        assertTrue(result.getSuccess());
        assertEquals("wecom", result.getChannel());
        assertEquals("{\"errcode\":0}", result.getRawResponse());
        verify(weComChannel).send(mockMessage);
    }

    @Test
    @DisplayName("send - 通道未配置抛出操作失败异常")
    void send_NotConfigured_ThrowsException() {
        // arrange
        when(weComChannel.isConfigured()).thenReturn(false);

        // act + assert
        BusinessException ex = assertThrows(BusinessException.class, () ->
                notificationService.send(NotificationChannelEnum.WECOM,
                        NotificationTemplateEnum.APPROVAL_PENDING, null));
        assertTrue(ex.getMessage().contains("通道未配置"));
        verify(weComChannel, never()).send(any());
    }

    // endregion

    // region test

    @Test
    @DisplayName("test - 无效通道类型抛出参数错误")
    void test_InvalidChannel_ThrowsException() {
        // arrange
        NotificationTestRequest request = new NotificationTestRequest();
        request.setChannel("invalid");
        request.setTemplateType("APPROVAL_PENDING");

        // act + assert
        assertThrows(BusinessException.class, () -> notificationService.test(request));
    }

    @Test
    @DisplayName("test - 无效模板类型抛出参数错误")
    void test_InvalidTemplate_ThrowsException() {
        // arrange
        NotificationTestRequest request = new NotificationTestRequest();
        request.setChannel("wecom");
        request.setTemplateType("INVALID");

        // act + assert
        assertThrows(BusinessException.class, () -> notificationService.test(request));
    }

    @Test
    @DisplayName("test - 合法请求透传到 send 并返回结果")
    void test_ValidRequest_RoutesToSend() {
        // arrange
        NotificationMessage mockMessage = NotificationMessage.builder()
                .template(NotificationTemplateEnum.TASK_FAILED)
                .title("任务失败告警")
                .content("正文")
                .build();
        when(templateRenderer.render(eq(NotificationTemplateEnum.TASK_FAILED), any()))
                .thenReturn(mockMessage);
        when(weComChannel.isConfigured()).thenReturn(true);
        when(weComChannel.send(mockMessage))
                .thenReturn(NotificationSendResultVO.success("wecom", "{\"errcode\":0}"));

        NotificationTestRequest request = new NotificationTestRequest();
        request.setChannel("wecom");
        request.setTemplateType("TASK_FAILED");
        request.setParams(Map.of("taskId", "task_001"));

        // act
        NotificationSendResultVO result = notificationService.test(request);

        // assert
        assertTrue(result.getSuccess());
        verify(weComChannel).send(mockMessage);
    }

    // endregion

    // region dispatch（M6.6 扩展）

    @Test
    @DisplayName("dispatch - 透传到 NotificationDispatcher 并返回结果")
    void dispatch_DelegatesToDispatcher() {
        // arrange
        when(dispatcher.dispatch(eq(NotificationTemplateEnum.APPROVAL_PENDING), anyMap(),
                eq(1L), eq(100L), eq(300L), eq(0)))
                .thenReturn(true);

        // act
        boolean result = notificationService.dispatch(
                NotificationTemplateEnum.APPROVAL_PENDING, Map.of("approvalId", "apr_001"),
                1L, 100L, 300L);

        // assert
        assertTrue(result);
        verify(dispatcher).dispatch(eq(NotificationTemplateEnum.APPROVAL_PENDING), anyMap(),
                eq(1L), eq(100L), eq(300L), eq(0));
    }

    // endregion

    // region 重试队列查询（M6.6 扩展）

    @Test
    @DisplayName("getRetryQueueSize - 返回 Redis 队列长度")
    void getRetryQueueSize_ReturnsRedisSize() {
        // arrange
        when(retryQueue.size()).thenReturn(5);

        // act
        long size = notificationService.getRetryQueueSize();

        // assert
        assertEquals(5L, size);
    }

    @Test
    @DisplayName("getRetryStats - 聚合队列长度 + 总尝试次数 + 成功率")
    void getRetryStats_AggregatesAllFields() {
        // arrange
        when(retryQueue.size()).thenReturn(3);
        when(attemptService.countAttempts(null, null)).thenReturn(100L);
        when(attemptService.calculateSuccessRate(null, null)).thenReturn(0.8);

        // act
        RetryQueueStatsVO stats = notificationService.getRetryStats();

        // assert
        assertEquals(3L, stats.getQueueSize());
        assertEquals(100L, stats.getTotalAttempts());
        assertEquals(80L, stats.getSuccessCount()); // 0.8 * 100 = 80
        assertEquals(20L, stats.getFailureCount()); // 100 - 80 = 20
        assertEquals(0.8, stats.getSuccessRate(), 0.0001);
        assertEquals(3L, stats.getAlertCount()); // 等于 queueSize
    }

    @Test
    @DisplayName("getRetryStats - 无记录时所有计数为 0")
    void getRetryStats_NoAttempts_AllZeros() {
        // arrange
        when(retryQueue.size()).thenReturn(0);
        when(attemptService.countAttempts(null, null)).thenReturn(0L);

        // act
        RetryQueueStatsVO stats = notificationService.getRetryStats();

        // assert
        assertEquals(0L, stats.getQueueSize());
        assertEquals(0L, stats.getTotalAttempts());
        assertEquals(0L, stats.getSuccessCount());
        assertEquals(0L, stats.getFailureCount());
        assertEquals(0.0, stats.getSuccessRate());
        assertEquals(0L, stats.getAlertCount());
    }

    // endregion
}
