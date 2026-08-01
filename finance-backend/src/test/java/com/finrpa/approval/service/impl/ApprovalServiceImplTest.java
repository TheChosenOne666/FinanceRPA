package com.finrpa.approval.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
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
    private ObjectMapper objectMapper;

    @InjectMocks
    private ApprovalServiceImpl approvalService;

    // region createApproval

    @Test
    @DisplayName("createApproval - high 风险创建审批单成功")
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
    }

    @Test
    @DisplayName("processTimeoutApprovals - 有超时审批单标记为 TIMEOUT")
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
        verify(approvalPubSubService, times(2)).publishResponse(any(ApprovalRequestEO.class));
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
