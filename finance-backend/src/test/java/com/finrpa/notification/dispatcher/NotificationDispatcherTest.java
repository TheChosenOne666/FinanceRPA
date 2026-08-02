package com.finrpa.notification.dispatcher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finrpa.notification.channels.NotificationChannel;
import com.finrpa.notification.constant.NotificationConstant;
import com.finrpa.notification.dto.NotificationMessage;
import com.finrpa.notification.dto.NotificationRetryTask;
import com.finrpa.notification.dto.response.NotificationSendResultVO;
import com.finrpa.notification.entity.NotificationAttemptEO;
import com.finrpa.notification.enums.NotificationChannelEnum;
import com.finrpa.notification.enums.NotificationTemplateEnum;
import com.finrpa.notification.service.NotificationAttemptService;
import com.finrpa.notification.templates.NotificationTemplateRenderer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RList;
import org.redisson.api.RedissonClient;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 通知调度器单元测试（M6.6 扩展）
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@ExtendWith(MockitoExtension.class)
class NotificationDispatcherTest {

    @Mock
    private NotificationChannel weComChannel;

    @Mock
    private NotificationChannel dingTalkChannel;

    @Mock
    private NotificationTemplateRenderer templateRenderer;

    @Mock
    private NotificationAttemptService attemptService;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RList<String> retryQueue;

    private NotificationDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new NotificationDispatcher();
        ReflectionTestUtils.setField(dispatcher, "channels", List.of(weComChannel, dingTalkChannel));
        ReflectionTestUtils.setField(dispatcher, "templateRenderer", templateRenderer);
        ReflectionTestUtils.setField(dispatcher, "attemptService", attemptService);
        ReflectionTestUtils.setField(dispatcher, "redissonClient", redissonClient);
        ReflectionTestUtils.setField(dispatcher, "objectMapper", new ObjectMapper());

        lenient().when(weComChannel.getChannel()).thenReturn(NotificationChannelEnum.WECOM);
        lenient().when(dingTalkChannel.getChannel()).thenReturn(NotificationChannelEnum.DINGTALK);
    }

    // region dispatch - 主通道成功

    @Test
    @DisplayName("dispatch - 主通道企微成功直接返回，不调用钉钉")
    void dispatch_PrimarySuccess_NoFallback() {
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
        boolean result = dispatcher.dispatch(
                NotificationTemplateEnum.APPROVAL_PENDING, Map.of("approvalId", "apr_001"),
                1L, 100L, null, 0);

        // assert
        assertTrue(result);
        verify(weComChannel).send(mockMessage);
        verify(dingTalkChannel, never()).send(any());
        verify(attemptService, times(1)).record(any(NotificationAttemptEO.class));
    }

    // endregion

    // region dispatch - Fallback 成功

    @Test
    @DisplayName("dispatch - 主通道失败 Fallback 到钉钉成功")
    void dispatch_PrimaryFailureFallbackSuccess_Succeeds() {
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
                .thenReturn(NotificationSendResultVO.failure("wecom", "企微返回错误"));
        when(dingTalkChannel.isConfigured()).thenReturn(true);
        when(dingTalkChannel.send(mockMessage))
                .thenReturn(NotificationSendResultVO.success("dingtalk", "{\"errcode\":0}"));

        // act
        boolean result = dispatcher.dispatch(
                NotificationTemplateEnum.TASK_FAILED, Map.of("taskId", "task_001"),
                null, 100L, null, 0);

        // assert
        assertTrue(result);
        verify(weComChannel).send(mockMessage);
        verify(dingTalkChannel).send(mockMessage);
        verify(attemptService, times(2)).record(any(NotificationAttemptEO.class));
    }

    // endregion

    // region dispatch - 全部失败入重试队列

    @Test
    @DisplayName("dispatch - 主通道与 Fallback 均失败入重试队列")
    void dispatch_AllChannelsFail_EnqueueRetry() {
        // arrange
        NotificationMessage mockMessage = NotificationMessage.builder()
                .template(NotificationTemplateEnum.APPROVAL_TIMEOUT)
                .title("审批超时告警")
                .content("正文")
                .build();
        when(templateRenderer.render(eq(NotificationTemplateEnum.APPROVAL_TIMEOUT), any()))
                .thenReturn(mockMessage);
        when(weComChannel.isConfigured()).thenReturn(true);
        when(weComChannel.send(mockMessage))
                .thenReturn(NotificationSendResultVO.failure("wecom", "失败"));
        when(dingTalkChannel.isConfigured()).thenReturn(true);
        when(dingTalkChannel.send(mockMessage))
                .thenReturn(NotificationSendResultVO.failure("dingtalk", "失败"));
        doReturn(retryQueue).when(redissonClient).getList(NotificationConstant.RETRY_QUEUE_KEY);
        when(retryQueue.size()).thenReturn(1);

        // act
        boolean result = dispatcher.dispatch(
                NotificationTemplateEnum.APPROVAL_TIMEOUT, Map.of("approvalId", "apr_001"),
                1L, 100L, null, 0);

        // assert
        assertFalse(result);
        verify(retryQueue, times(1)).add(anyString());
    }

    @Test
    @DisplayName("dispatch - 无通道配置直接入重试队列")
    void dispatch_NoChannelConfigured_EnqueueRetry() {
        // arrange
        NotificationMessage mockMessage = NotificationMessage.builder()
                .template(NotificationTemplateEnum.NEEDS_HUMAN)
                .title("人工接管告警")
                .content("正文")
                .build();
        when(templateRenderer.render(eq(NotificationTemplateEnum.NEEDS_HUMAN), any()))
                .thenReturn(mockMessage);
        when(weComChannel.isConfigured()).thenReturn(false);
        when(dingTalkChannel.isConfigured()).thenReturn(false);
        doReturn(retryQueue).when(redissonClient).getList(NotificationConstant.RETRY_QUEUE_KEY);

        // act
        boolean result = dispatcher.dispatch(
                NotificationTemplateEnum.NEEDS_HUMAN, Map.of(),
                null, 100L, null, 0);

        // assert
        assertFalse(result);
        verify(weComChannel, never()).send(any());
        verify(dingTalkChannel, never()).send(any());
        verify(retryQueue, times(1)).add(anyString());
    }

    // endregion

    // region processRetryTask - 超过最大重试次数告警

    @Test
    @DisplayName("processRetryTask - 超过 MAX_RETRY_COUNT 阈值直接告警，不再发送")
    void processRetryTask_OverMaxRetry_ReturnsFalseWithoutSend() {
        // arrange: retryCount=3 (MAX=3)，再 +1 = 4 超阈值
        NotificationRetryTask retryTask = NotificationRetryTask.builder()
                .template(NotificationTemplateEnum.APPROVAL_PENDING)
                .params(Map.of("approvalId", "apr_001"))
                .approvalId(1L)
                .taskId(100L)
                .retryCount(NotificationConstant.MAX_RETRY_COUNT)
                .build();

        // act
        boolean result = dispatcher.processRetryTask(retryTask);

        // assert
        assertFalse(result);
        verify(weComChannel, never()).send(any());
        verify(dingTalkChannel, never()).send(any());
    }

    @Test
    @DisplayName("processRetryTask - 未超阈值时正常调用 dispatch")
    void processRetryTask_UnderMax_CallsDispatch() {
        // arrange
        NotificationRetryTask retryTask = NotificationRetryTask.builder()
                .template(NotificationTemplateEnum.APPROVAL_PENDING)
                .params(Map.of("approvalId", "apr_001"))
                .approvalId(1L)
                .taskId(100L)
                .retryCount(0)
                .build();
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
        boolean result = dispatcher.processRetryTask(retryTask);

        // assert
        assertTrue(result);
        verify(weComChannel).send(mockMessage);
    }

    // endregion

    // region attempt 记录写入

    @Test
    @DisplayName("dispatch - 写入 attempt 记录含正确字段（通道 + 模板 + 成功标识）")
    void dispatch_WritesAttemptRecord_WithCorrectFields() {
        // arrange
        NotificationMessage mockMessage = NotificationMessage.builder()
                .template(NotificationTemplateEnum.RISK_ESCALATION)
                .title("风险升级告警")
                .content("正文")
                .build();
        when(templateRenderer.render(eq(NotificationTemplateEnum.RISK_ESCALATION), any()))
                .thenReturn(mockMessage);
        when(weComChannel.isConfigured()).thenReturn(true);
        when(weComChannel.send(mockMessage))
                .thenReturn(NotificationSendResultVO.success("wecom", "{\"errcode\":0}"));

        // act
        dispatcher.dispatch(
                NotificationTemplateEnum.RISK_ESCALATION, Map.of("taskId", "task_001"),
                1L, 100L, 300L, 0);

        // assert
        ArgumentCaptor<NotificationAttemptEO> captor = ArgumentCaptor.forClass(NotificationAttemptEO.class);
        verify(attemptService).record(captor.capture());
        NotificationAttemptEO attempt = captor.getValue();
        assertEquals("wecom", attempt.getChannel());
        assertEquals("RISK_ESCALATION", attempt.getTemplate());
        assertEquals("风险升级告警", attempt.getTitle());
        assertEquals(1, attempt.getSuccess());
        assertEquals(0, attempt.getRetryCount());
        assertEquals(1L, attempt.getApprovalId());
        assertEquals(100L, attempt.getTaskId());
        assertEquals(300L, attempt.getTargetUserId());
        assertNull(attempt.getEnqueuedAt()); // 首次发送 enqueuedAt 为 null
    }

    // endregion
}
