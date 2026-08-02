package com.finrpa.approval.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.finrpa.agent.service.TaskService;
import com.finrpa.ai.client.AiServiceClient;
import com.finrpa.ai.client.dto.TaskTriggerResponse;
import com.finrpa.approval.constant.ApprovalConstant;
import com.finrpa.approval.dto.request.ApprovalQueryRequest;
import com.finrpa.approval.dto.response.ApprovalResultResponse;
import com.finrpa.approval.entity.ApprovalRequestEO;
import com.finrpa.approval.mapper.ApprovalRequestMapper;
import com.finrpa.approval.service.ApprovalPubSubService;
import com.finrpa.approval.service.ApprovalRouteService;
import com.finrpa.common.exception.BusinessException;
import com.finrpa.common.response.ErrorCode;
import com.finrpa.notification.enums.NotificationTemplateEnum;
import com.finrpa.notification.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 审批服务实现单元测试（M6.3）
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@ExtendWith(MockitoExtension.class)
class ApprovalServiceImplTest {

    @Mock
    private ApprovalRequestMapper approvalRequestMapper;

    @Mock
    private ApprovalRouteService approvalRouteService;

    @Mock
    private ApprovalPubSubService approvalPubSubService;

    @Mock
    private AiServiceClient aiServiceClient;

    @Mock
    private TaskService taskService;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private ApprovalServiceImpl approvalService;

    // region createApproval

    @Test
    @DisplayName("createApproval - high 风险创建审批单成功（超时 30 分钟）")
    void createApproval_HighRisk_Success() {
        // arrange
        when(approvalRouteService.routeByRiskLevel("high"))
                .thenReturn(com.finrpa.approval.enums.ApprovalRouteEnum.DEPARTMENT);
        when(approvalRequestMapper.insert(any(ApprovalRequestEO.class))).thenReturn(1);

        // act
        ApprovalRequestEO result = approvalService.createApproval(
                100L, 1L, 200L, 300L, "high", "涉及转账操作", "{\"goal\":\"test\"}");

        // assert
        assertNotNull(result);
        assertEquals(100L, result.getTaskId());
        assertEquals("high", result.getRiskLevel());
        assertEquals("department", result.getApprovalRoute());
        assertEquals("PENDING", result.getStatus());
        assertEquals(30, result.getTimeoutMinutes());
        assertNotNull(result.getTimeoutAt());
        assertEquals("涉及转账操作", result.getRiskReasoning());
        assertEquals("{\"goal\":\"test\"}", result.getRequestPayload());

        // verify Pub/Sub
        verify(approvalPubSubService).publishRequest(any(ApprovalRequestEO.class));

        // verify 通知触发（M6.6 APPROVAL_PENDING）—— approvalId 在 mock 测试中为 null（未走 MyBatis 雪花赋值）
        verify(notificationService).dispatch(eq(NotificationTemplateEnum.APPROVAL_PENDING),
                anyMap(), isNull(), eq(100L), eq(300L));
    }

    @Test
    @DisplayName("createApproval - critical 风险创建审批单成功（超时 60 分钟）")
    void createApproval_CriticalRisk_Success() {
        // arrange
        when(approvalRouteService.routeByRiskLevel("critical"))
                .thenReturn(com.finrpa.approval.enums.ApprovalRouteEnum.COMPLIANCE);
        when(approvalRequestMapper.insert(any(ApprovalRequestEO.class))).thenReturn(1);

        // act
        ApprovalRequestEO result = approvalService.createApproval(
                101L, 1L, 200L, 300L, "critical", "命中敏感数据+高风险操作", "{\"goal\":\"test\"}");

        // assert
        assertNotNull(result);
        assertEquals(101L, result.getTaskId());
        assertEquals("critical", result.getRiskLevel());
        assertEquals("compliance", result.getApprovalRoute());
        assertEquals("PENDING", result.getStatus());
        assertEquals(60, result.getTimeoutMinutes());
        assertNotNull(result.getTimeoutAt());
        assertEquals("命中敏感数据+高风险操作", result.getRiskReasoning());

        // verify Pub/Sub
        verify(approvalPubSubService).publishRequest(any(ApprovalRequestEO.class));
    }

    @Test
    @DisplayName("createApproval - low 风险抛出异常（无需审批）")
    void createApproval_LowRisk_ThrowsException() {
        when(approvalRouteService.routeByRiskLevel("low"))
                .thenReturn(com.finrpa.approval.enums.ApprovalRouteEnum.AUTO);

        assertThrows(BusinessException.class, () ->
                approvalService.createApproval(100L, 1L, 200L, 300L, "low", "", ""));
    }

    // endregion

    // region approve

    @Test
    @DisplayName("approve - 审批通过成功并触发 Python")
    void approve_Success_TriggersPython() throws Exception {
        // arrange
        ApprovalRequestEO approval = buildPendingApproval(800L, 100L, "high", "department");
        approval.setRequestPayload("{\"taskId\":\"100\"}");
        when(approvalRequestMapper.selectById(800L)).thenReturn(approval);
        when(approvalRequestMapper.updateById(any(ApprovalRequestEO.class))).thenReturn(1);

        com.finrpa.ai.client.dto.TaskTriggerRequest triggerRequest =
                new com.finrpa.ai.client.dto.TaskTriggerRequest();
        when(objectMapper.readValue(anyString(), eq(com.finrpa.ai.client.dto.TaskTriggerRequest.class)))
                .thenReturn(triggerRequest);
        TaskTriggerResponse triggerResponse = new TaskTriggerResponse();
        triggerResponse.setStatus("QUEUED");
        when(aiServiceClient.triggerTask(any())).thenReturn(triggerResponse);

        // act
        ApprovalRequestEO result = approvalService.approve(800L, 500L, "同意操作");

        // assert
        assertEquals("APPROVED", result.getStatus());
        assertEquals(500L, result.getApproverId());
        assertEquals("同意操作", result.getApproveReason());
        assertNotNull(result.getApprovedAt());

        // verify Pub/Sub + Python trigger
        verify(approvalPubSubService).publishResponse(any(ApprovalRequestEO.class));
        verify(aiServiceClient).triggerTask(any());
    }

    @Test
    @DisplayName("approve - 审批单不存在抛出异常")
    void approve_NotFound_ThrowsException() {
        when(approvalRequestMapper.selectById(999L)).thenReturn(null);

        assertThrows(BusinessException.class, () -> approvalService.approve(999L, 500L, ""));
    }

    @Test
    @DisplayName("approve - 已审批的单不能重复操作")
    void approve_AlreadyProcessed_ThrowsException() {
        ApprovalRequestEO approval = buildPendingApproval(800L, 100L, "high", "department");
        approval.setStatus("APPROVED");
        when(approvalRequestMapper.selectById(800L)).thenReturn(approval);

        assertThrows(BusinessException.class, () -> approvalService.approve(800L, 500L, ""));
    }

    // endregion

    // region reject

    @Test
    @DisplayName("reject - 审批拒绝成功")
    void reject_Success() {
        ApprovalRequestEO approval = buildPendingApproval(800L, 100L, "critical", "compliance");
        when(approvalRequestMapper.selectById(800L)).thenReturn(approval);
        when(approvalRequestMapper.updateById(any(ApprovalRequestEO.class))).thenReturn(1);

        ApprovalRequestEO result = approvalService.reject(800L, 500L, "风险过高");

        assertEquals("REJECTED", result.getStatus());
        assertEquals(500L, result.getApproverId());
        assertEquals("风险过高", result.getRejectReason());
        assertNotNull(result.getApprovedAt());

        verify(approvalPubSubService).publishResponse(any(ApprovalRequestEO.class));
        // reject 不触发 Python
        verify(aiServiceClient, never()).triggerTask(any());
    }

    // endregion

    // region getApprovalResultByTaskId

    @Test
    @DisplayName("getApprovalResultByTaskId - 找到审批单返回结果")
    void getApprovalResultByTaskId_Found() {
        ApprovalRequestEO approval = buildPendingApproval(800L, 100L, "high", "department");
        approval.setStatus("APPROVED");
        when(approvalRequestMapper.selectOne(any(Wrapper.class))).thenReturn(approval);

        ApprovalResultResponse result = approvalService.getApprovalResultByTaskId(100L);

        assertEquals(800L, result.getApprovalId());
        assertEquals("APPROVED", result.getStatus());
        assertTrue(result.isApproved());
        assertTrue(result.isTerminal());
    }

    @Test
    @DisplayName("getApprovalResultByTaskId - 未找到审批单返回 NOT_FOUND")
    void getApprovalResultByTaskId_NotFound() {
        when(approvalRequestMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        ApprovalResultResponse result = approvalService.getApprovalResultByTaskId(999L);

        assertEquals("NOT_FOUND", result.getStatus());
        assertFalse(result.isApproved());
        assertFalse(result.isTerminal());
    }

    // endregion

    // region processTimeoutApprovals

    @Test
    @DisplayName("processTimeoutApprovals - 无超时审批单返回 0")
    void processTimeoutApprovals_NoTimeout() {
        when(approvalRequestMapper.selectList(any(Wrapper.class)))
                .thenReturn(Collections.emptyList());

        int count = approvalService.processTimeoutApprovals();

        assertEquals(0, count);
        // 无超时时不应调用任务终止或 Python 通知
        verify(taskService, never()).abortTask(anyLong());
        verify(aiServiceClient, never()).abortTask(anyString());
    }

    @Test
    @DisplayName("processTimeoutApprovals - 有超时审批单标记为 TIMEOUT + 终止任务 + 通知 Python")
    void processTimeoutApprovals_HasTimeout() {
        ApprovalRequestEO approval1 = buildPendingApproval(801L, 100L, "high", "department");
        ApprovalRequestEO approval2 = buildPendingApproval(802L, 101L, "critical", "compliance");
        when(approvalRequestMapper.selectList(any(Wrapper.class)))
                .thenReturn(List.of(approval1, approval2));
        when(approvalRequestMapper.updateById(any(ApprovalRequestEO.class))).thenReturn(1);

        int count = approvalService.processTimeoutApprovals();

        assertEquals(2, count);
        assertEquals("TIMEOUT", approval1.getStatus());
        assertEquals("TIMEOUT", approval2.getStatus());
        assertEquals("审批超时自动拒绝", approval1.getRejectReason());
        assertEquals("审批超时自动拒绝", approval2.getRejectReason());
        assertNotNull(approval1.getApprovedAt());
        assertNotNull(approval2.getApprovedAt());

        // 验证 Pub/Sub 通知
        verify(approvalPubSubService, times(2)).publishResponse(any(ApprovalRequestEO.class));

        // 验证 Java 任务状态更新为 ABORTED
        verify(taskService, times(1)).abortTask(100L);
        verify(taskService, times(1)).abortTask(101L);

        // 验证 Python 通知终止任务（防御性调用）
        verify(aiServiceClient, times(1)).abortTask("100");
        verify(aiServiceClient, times(1)).abortTask("101");

        // 验证通知触发（M6.6 APPROVAL_TIMEOUT × 2）
        verify(notificationService, times(2)).dispatch(eq(NotificationTemplateEnum.APPROVAL_TIMEOUT),
                anyMap(), any(), any(), any());
    }

    @Test
    @DisplayName("processTimeoutApprovals - 任务状态更新失败不影响超时处理主流程")
    void processTimeoutApprovals_TaskAbortFailure_DoesNotAffectMain() {
        ApprovalRequestEO approval = buildPendingApproval(803L, 102L, "high", "department");
        when(approvalRequestMapper.selectList(any(Wrapper.class)))
                .thenReturn(List.of(approval));
        when(approvalRequestMapper.updateById(any(ApprovalRequestEO.class))).thenReturn(1);
        // 任务终止抛出异常
        doThrow(new BusinessException(ErrorCode.OPERATION_ERROR, "任务已结束"))
                .when(taskService).abortTask(102L);

        int count = approvalService.processTimeoutApprovals();

        // 主流程仍应成功返回 1
        assertEquals(1, count);
        assertEquals("TIMEOUT", approval.getStatus());
        // Pub/Sub 通知仍应发布
        verify(approvalPubSubService, times(1)).publishResponse(any(ApprovalRequestEO.class));
        // Python 通知仍应调用
        verify(aiServiceClient, times(1)).abortTask("102");
    }

    @Test
    @DisplayName("processTimeoutApprovals - Python 通知失败不影响超时处理主流程")
    void processTimeoutApprovals_PythonAbortFailure_DoesNotAffectMain() {
        ApprovalRequestEO approval = buildPendingApproval(804L, 103L, "critical", "compliance");
        when(approvalRequestMapper.selectList(any(Wrapper.class)))
                .thenReturn(List.of(approval));
        when(approvalRequestMapper.updateById(any(ApprovalRequestEO.class))).thenReturn(1);
        // Python abort 抛出异常（预期行为：审批未通过时 Python 无活跃任务）
        doThrow(new RuntimeException("404 Not Found"))
                .when(aiServiceClient).abortTask("103");

        int count = approvalService.processTimeoutApprovals();

        // 主流程仍应成功返回 1
        assertEquals(1, count);
        assertEquals("TIMEOUT", approval.getStatus());
        // 任务状态更新仍应执行
        verify(taskService, times(1)).abortTask(103L);
    }

    // endregion

    // region 辅助方法

    /**
     * 构建待审批的审批单
     */
    private ApprovalRequestEO buildPendingApproval(Long approvalId, Long taskId,
                                                    String riskLevel, String route) {
        ApprovalRequestEO approval = new ApprovalRequestEO();
        approval.setApprovalId(approvalId);
        approval.setTaskId(taskId);
        approval.setOrgId(1L);
        approval.setRiskLevel(riskLevel);
        approval.setApprovalRoute(route);
        approval.setStatus(ApprovalConstant.APPROVAL_STATUS_PENDING);
        approval.setTimeoutMinutes(30);
        return approval;
    }

    // endregion
}
