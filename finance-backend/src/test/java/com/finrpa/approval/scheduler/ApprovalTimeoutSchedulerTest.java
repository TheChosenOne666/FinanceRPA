package com.finrpa.approval.scheduler;

import com.finrpa.approval.service.ApprovalService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

/**
 * 审批超时检测调度器单元测试（M6.4）
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@ExtendWith(MockitoExtension.class)
class ApprovalTimeoutSchedulerTest {

    @Mock
    private ApprovalService approvalService;

    @InjectMocks
    private ApprovalTimeoutScheduler scheduler;

    @Test
    @DisplayName("scanTimeoutApprovals - 正常扫描委托给 ApprovalService")
    void scanTimeoutApprovals_DelegatesToApprovalService() {
        when(approvalService.processTimeoutApprovals()).thenReturn(3);

        assertDoesNotThrow(() -> scheduler.scanTimeoutApprovals());

        verify(approvalService, times(1)).processTimeoutApprovals();
    }

    @Test
    @DisplayName("scanTimeoutApprovals - 无超时审批单时正常返回")
    void scanTimeoutApprovals_NoTimeout() {
        when(approvalService.processTimeoutApprovals()).thenReturn(0);

        assertDoesNotThrow(() -> scheduler.scanTimeoutApprovals());

        verify(approvalService, times(1)).processTimeoutApprovals();
    }

    @Test
    @DisplayName("scanTimeoutApprovals - ApprovalService 抛出异常时调度器不向外抛出（避免调度器停止）")
    void scanTimeoutApprovals_ServiceThrowsException_DoesNotPropagate() {
        when(approvalService.processTimeoutApprovals())
                .thenThrow(new RuntimeException("数据库连接异常"));

        // 调度器内部捕获异常，不应向外抛出（否则 Spring Scheduler 会停止调度）
        assertDoesNotThrow(() -> scheduler.scanTimeoutApprovals());

        verify(approvalService, times(1)).processTimeoutApprovals();
    }
}
