package com.finrpa.approval.service.impl;

import com.finrpa.approval.enums.ApprovalRouteEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 审批路由服务实现单元测试（M6.3）
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@ExtendWith(MockitoExtension.class)
class ApprovalRouteServiceImplTest {

    @InjectMocks
    private ApprovalRouteServiceImpl approvalRouteService;

    // region routeByRiskLevel

    @Test
    @DisplayName("routeByRiskLevel - high 风险路由到 department")
    void routeByRiskLevel_High_ReturnsDepartment() {
        ApprovalRouteEnum route = approvalRouteService.routeByRiskLevel("high");
        assertEquals(ApprovalRouteEnum.DEPARTMENT, route);
    }

    @Test
    @DisplayName("routeByRiskLevel - critical 风险路由到 compliance")
    void routeByRiskLevel_Critical_ReturnsCompliance() {
        ApprovalRouteEnum route = approvalRouteService.routeByRiskLevel("critical");
        assertEquals(ApprovalRouteEnum.COMPLIANCE, route);
    }

    @Test
    @DisplayName("routeByRiskLevel - low 风险路由到 auto")
    void routeByRiskLevel_Low_ReturnsAuto() {
        ApprovalRouteEnum route = approvalRouteService.routeByRiskLevel("low");
        assertEquals(ApprovalRouteEnum.AUTO, route);
    }

    @Test
    @DisplayName("routeByRiskLevel - medium 风险路由到 auto")
    void routeByRiskLevel_Medium_ReturnsAuto() {
        ApprovalRouteEnum route = approvalRouteService.routeByRiskLevel("medium");
        assertEquals(ApprovalRouteEnum.AUTO, route);
    }

    @Test
    @DisplayName("routeByRiskLevel - null 风险路由到 auto")
    void routeByRiskLevel_Null_ReturnsAuto() {
        ApprovalRouteEnum route = approvalRouteService.routeByRiskLevel(null);
        assertEquals(ApprovalRouteEnum.AUTO, route);
    }

    // endregion

    // region needsHumanApproval

    @Test
    @DisplayName("needsHumanApproval - high 风险需要人工审批")
    void needsHumanApproval_High_ReturnsTrue() {
        assertTrue(approvalRouteService.needsHumanApproval("high"));
    }

    @Test
    @DisplayName("needsHumanApproval - critical 风险需要人工审批")
    void needsHumanApproval_Critical_ReturnsTrue() {
        assertTrue(approvalRouteService.needsHumanApproval("critical"));
    }

    @Test
    @DisplayName("needsHumanApproval - low 风险不需要人工审批")
    void needsHumanApproval_Low_ReturnsFalse() {
        assertFalse(approvalRouteService.needsHumanApproval("low"));
    }

    @Test
    @DisplayName("needsHumanApproval - medium 风险不需要人工审批")
    void needsHumanApproval_Medium_ReturnsFalse() {
        assertFalse(approvalRouteService.needsHumanApproval("medium"));
    }

    // endregion
}
