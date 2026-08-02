package com.finrpa.notification.templates;

import com.finrpa.common.exception.BusinessException;
import com.finrpa.notification.dto.NotificationMessage;
import com.finrpa.notification.enums.NotificationTemplateEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 通知模板渲染器单元测试（M6.6）
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
class NotificationTemplateRendererTest {

    private final NotificationTemplateRenderer renderer = new NotificationTemplateRenderer();

    // region APPROVAL_PENDING

    @Test
    @DisplayName("render APPROVAL_PENDING - 完整参数渲染成功")
    void renderApprovalPending_FullParams_Success() {
        // arrange
        Map<String, Object> params = new HashMap<>();
        params.put("approvalId", 12345L);
        params.put("taskId", 67890L);
        params.put("riskLevel", "high");
        params.put("approvalRoute", "department");
        params.put("timeoutMinutes", 30);
        params.put("riskReasoning", "涉及大额转账");

        // act
        NotificationMessage result = renderer.render(NotificationTemplateEnum.APPROVAL_PENDING, params);

        // assert
        assertEquals(NotificationTemplateEnum.APPROVAL_PENDING, result.getTemplate());
        assertEquals("审批待处理通知", result.getTitle());
        assertTrue(result.getContent().contains("审批待处理"));
        assertTrue(result.getContent().contains("12345"));
        assertTrue(result.getContent().contains("67890"));
        assertTrue(result.getContent().contains("high"));
        assertTrue(result.getContent().contains("department"));
        assertTrue(result.getContent().contains("30"));
        assertTrue(result.getContent().contains("涉及大额转账"));
    }

    @Test
    @DisplayName("render APPROVAL_PENDING - 空参数使用占位符")
    void renderApprovalPending_EmptyParams_UsesPlaceholder() {
        // act
        NotificationMessage result = renderer.render(NotificationTemplateEnum.APPROVAL_PENDING, null);

        // assert
        assertNotNull(result);
        assertTrue(result.getContent().contains("-"));
    }

    // endregion

    // region APPROVAL_TIMEOUT

    @Test
    @DisplayName("render APPROVAL_TIMEOUT - 渲染成功")
    void renderApprovalTimeout_Success() {
        // arrange
        Map<String, Object> params = Map.of(
                "approvalId", "apr_001",
                "taskId", "task_001",
                "riskLevel", "critical",
                "timeoutMinutes", 60);

        // act
        NotificationMessage result = renderer.render(NotificationTemplateEnum.APPROVAL_TIMEOUT, params);

        // assert
        assertEquals("审批超时告警", result.getTitle());
        assertTrue(result.getContent().contains("apr_001"));
        assertTrue(result.getContent().contains("task_001"));
        assertTrue(result.getContent().contains("critical"));
        assertTrue(result.getContent().contains("60"));
    }

    // endregion

    // region TASK_FAILED

    @Test
    @DisplayName("render TASK_FAILED - 渲染成功")
    void renderTaskFailed_Success() {
        // arrange
        Map<String, Object> params = Map.of(
                "taskId", "task_failed_001",
                "errorMessage", "网络超时",
                "failedAt", "2026-08-02 10:00:00");

        // act
        NotificationMessage result = renderer.render(NotificationTemplateEnum.TASK_FAILED, params);

        // assert
        assertEquals("任务失败告警", result.getTitle());
        assertTrue(result.getContent().contains("task_failed_001"));
        assertTrue(result.getContent().contains("网络超时"));
        assertTrue(result.getContent().contains("2026-08-02 10:00:00"));
    }

    // endregion

    // region NEEDS_HUMAN

    @Test
    @DisplayName("render NEEDS_HUMAN - 渲染成功")
    void renderNeedsHuman_Success() {
        // arrange
        Map<String, Object> params = Map.of(
                "needsHumanId", "nh_001",
                "taskId", "task_001",
                "reason", "LLM 三次重试均失败",
                "llmModel", "gpt-4");

        // act
        NotificationMessage result = renderer.render(NotificationTemplateEnum.NEEDS_HUMAN, params);

        // assert
        assertEquals("人工接管告警", result.getTitle());
        assertTrue(result.getContent().contains("nh_001"));
        assertTrue(result.getContent().contains("task_001"));
        assertTrue(result.getContent().contains("LLM 三次重试均失败"));
        assertTrue(result.getContent().contains("gpt-4"));
    }

    // endregion

    // region RISK_ESCALATION

    @Test
    @DisplayName("render RISK_ESCALATION - 渲染成功")
    void renderRiskEscalation_Success() {
        // arrange
        Map<String, Object> params = Map.of(
                "taskId", "task_001",
                "originalRiskLevel", "medium",
                "escalatedRiskLevel", "high",
                "reason", "LLM 判断升级");

        // act
        NotificationMessage result = renderer.render(NotificationTemplateEnum.RISK_ESCALATION, params);

        // assert
        assertEquals("风险等级升级告警", result.getTitle());
        assertTrue(result.getContent().contains("task_001"));
        assertTrue(result.getContent().contains("medium"));
        assertTrue(result.getContent().contains("high"));
        assertTrue(result.getContent().contains("LLM 判断升级"));
    }

    // endregion

    // region 异常场景

    @Test
    @DisplayName("render - 模板类型为 null 抛出参数错误")
    void render_NullTemplate_ThrowsException() {
        // act + assert
        assertThrows(BusinessException.class, () -> renderer.render(null, null));
    }

    // endregion
}
