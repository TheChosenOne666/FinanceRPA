package com.finrpa.notification.scheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finrpa.notification.constant.NotificationConstant;
import com.finrpa.notification.dispatcher.NotificationDispatcher;
import com.finrpa.notification.dto.NotificationRetryTask;
import com.finrpa.notification.enums.NotificationTemplateEnum;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 通知重试调度器单元测试（M6.6 扩展）
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@ExtendWith(MockitoExtension.class)
class NotificationRetrySchedulerTest {

    @Mock
    private NotificationDispatcher dispatcher;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RList<String> retryQueue;

    private NotificationRetryScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new NotificationRetryScheduler();
        ReflectionTestUtils.setField(scheduler, "dispatcher", dispatcher);
        ReflectionTestUtils.setField(scheduler, "redissonClient", redissonClient);
        ReflectionTestUtils.setField(scheduler, "objectMapper", new ObjectMapper());
    }

    @Test
    @DisplayName("scanRetryQueue - 空队列直接跳过")
    void scanRetryQueue_EmptyQueue_Skips() {
        // arrange
        doReturn(retryQueue).when(redissonClient).getList(NotificationConstant.RETRY_QUEUE_KEY);
        when(retryQueue.size()).thenReturn(0);

        // act
        scheduler.scanRetryQueue();

        // assert
        verify(dispatcher, never()).processRetryTask(any());
    }

    @Test
    @DisplayName("scanRetryQueue - 队列有任务时逐个出队并调用 dispatcher")
    void scanRetryQueue_WithTasks_ProcessesEach() throws Exception {
        // arrange
        NotificationRetryTask task1 = NotificationRetryTask.builder()
                .template(NotificationTemplateEnum.APPROVAL_PENDING)
                .params(Map.of("approvalId", "apr_001"))
                .approvalId(1L)
                .taskId(100L)
                .retryCount(0)
                .build();
        NotificationRetryTask task2 = NotificationRetryTask.builder()
                .template(NotificationTemplateEnum.TASK_FAILED)
                .params(Map.of("taskId", "task_002"))
                .taskId(200L)
                .retryCount(1)
                .build();

        ObjectMapper mapper = new ObjectMapper();
        String json1 = mapper.writeValueAsString(task1);
        String json2 = mapper.writeValueAsString(task2);

        doReturn(retryQueue).when(redissonClient).getList(NotificationConstant.RETRY_QUEUE_KEY);
        when(retryQueue.size()).thenReturn(2);
        when(retryQueue.readAll()).thenReturn(List.of(json1, json2));
        when(dispatcher.processRetryTask(any())).thenReturn(true);

        // act
        scheduler.scanRetryQueue();

        // assert
        verify(dispatcher, times(2)).processRetryTask(any(NotificationRetryTask.class));
        // 每个任务都应从队列中删除
        verify(retryQueue, times(2)).remove(anyString());
    }

    @Test
    @DisplayName("scanRetryQueue - 反序列化失败的任务从队列丢弃")
    void scanRetryQueue_InvalidJson_Discards() {
        // arrange
        doReturn(retryQueue).when(redissonClient).getList(NotificationConstant.RETRY_QUEUE_KEY);
        when(retryQueue.size()).thenReturn(1);
        when(retryQueue.readAll()).thenReturn(List.of("invalid-json"));
        when(retryQueue.remove("invalid-json")).thenReturn(true);

        // act
        scheduler.scanRetryQueue();

        // assert
        verify(dispatcher, never()).processRetryTask(any());
        verify(retryQueue).remove("invalid-json");
    }
}
