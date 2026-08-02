package com.finrpa.notification.service.impl;

import com.finrpa.notification.entity.NotificationAttemptEO;
import com.finrpa.notification.mapper.NotificationAttemptMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 通知尝试记录服务单元测试（M6.6 扩展）
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@ExtendWith(MockitoExtension.class)
class NotificationAttemptServiceImplTest {

    @Mock
    private NotificationAttemptMapper mapper;

    @InjectMocks
    private NotificationAttemptServiceImpl service;

    @Test
    @DisplayName("record - 写入记录后返回实体")
    void record_PersistsAttempt_ReturnsEntity() {
        // arrange
        NotificationAttemptEO attempt = new NotificationAttemptEO();
        attempt.setChannel("wecom");
        attempt.setTemplate("APPROVAL_PENDING");
        attempt.setSuccess(1);
        when(mapper.insert(any(NotificationAttemptEO.class))).thenReturn(1);

        // act
        NotificationAttemptEO result = service.record(attempt);

        // assert
        assertSame(attempt, result);
        verify(mapper).insert(attempt);
    }

    @Test
    @DisplayName("countAttempts - 无时间范围返回全量计数")
    void countAttempts_NoTimeRange_ReturnsTotal() {
        // arrange
        when(mapper.selectCount(any())).thenReturn(100L);

        // act
        long count = service.countAttempts(null, null);

        // assert
        assertEquals(100L, count);
    }

    @Test
    @DisplayName("calculateSuccessRate - 计算成功率（成功 / 总数）")
    void calculateSuccessRate_ComputesRatio() {
        // arrange
        // 第一次 selectCount 返回总数 100，第二次（带 success=1 条件）返回成功 80
        when(mapper.selectCount(any())).thenReturn(100L, 80L);

        // act
        double rate = service.calculateSuccessRate(null, null);

        // assert
        assertEquals(0.8, rate, 0.0001);
    }

    @Test
    @DisplayName("calculateSuccessRate - 无记录返回 0.0")
    void calculateSuccessRate_NoRecords_ReturnsZero() {
        // arrange
        when(mapper.selectCount(any())).thenReturn(0L);

        // act
        double rate = service.calculateSuccessRate(null, null);

        // assert
        assertEquals(0.0, rate);
    }
}
