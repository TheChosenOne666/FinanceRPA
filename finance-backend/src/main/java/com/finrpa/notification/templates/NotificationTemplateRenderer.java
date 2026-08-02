package com.finrpa.notification.templates;

import com.finrpa.common.exception.ThrowUtils;
import com.finrpa.common.response.ErrorCode;
import com.finrpa.notification.dto.NotificationMessage;
import com.finrpa.notification.enums.NotificationTemplateEnum;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 通知模板渲染器（M6.6）
 *
 * <p>按 {@link NotificationTemplateEnum} 将模板参数 {@code Map<String, Object>} 渲染为
 * 标题 + markdown 正文的 {@link NotificationMessage}。</p>
 *
 * <p>支持 5 种模板（对齐 system-design.md 6.10.1）：
 * <ul>
 *   <li>{@link NotificationTemplateEnum#APPROVAL_PENDING} 审批待处理</li>
 *   <li>{@link NotificationTemplateEnum#APPROVAL_TIMEOUT} 审批超时告警</li>
 *   <li>{@link NotificationTemplateEnum#TASK_FAILED} 任务失败</li>
 *   <li>{@link NotificationTemplateEnum#NEEDS_HUMAN} NEEDS_HUMAN 接管</li>
 *   <li>{@link NotificationTemplateEnum#RISK_ESCALATION} 风险等级升级</li>
 * </ul>
 * </p>
 *
 * <p>渲染策略：每个模板对应独立 render 方法，参数从 Map 安全提取（缺失字段使用 "-" 占位）。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Component
public class NotificationTemplateRenderer {

    /** 参数缺失时的占位符 */
    private static final String PLACEHOLDER = "-";

    /**
     * 渲染通知消息
     *
     * @param template 模板类型
     * @param params   模板参数（允许为 null，使用占位符填充）
     * @return 通知消息（含 title + content）
     */
    public NotificationMessage render(NotificationTemplateEnum template, Map<String, Object> params) {
        ThrowUtils.throwIf(template == null, ErrorCode.PARAMS_ERROR, "模板类型不能为空");
        Map<String, Object> safeParams = params == null ? Map.of() : params;

        // 按模板类型分发渲染
        return switch (template) {
            case APPROVAL_PENDING -> renderApprovalPending(safeParams);
            case APPROVAL_TIMEOUT -> renderApprovalTimeout(safeParams);
            case TASK_FAILED -> renderTaskFailed(safeParams);
            case NEEDS_HUMAN -> renderNeedsHuman(safeParams);
            case RISK_ESCALATION -> renderRiskEscalation(safeParams);
        };
    }

    /**
     * 渲染审批待处理模板
     *
     * <p>参数：approvalId / taskId / riskLevel / riskReasoning / timeoutMinutes / approvalRoute</p>
     *
     * @param params 模板参数
     * @return 通知消息
     */
    private NotificationMessage renderApprovalPending(Map<String, Object> params) {
        String title = "审批待处理通知";
        String content = String.format("""
                ## 审批待处理

                > 风险等级 **%s** 任务已进入审批流程，请尽快处理。

                **审批单编号**：%s
                **任务编号**：%s
                **风险等级**：%s
                **审批路由**：%s
                **超时时间**：%s 分钟
                **风险理由**：%s

                请前往 FinanceRPA 审批中心处理。
                """,
                getStr(params, "riskLevel"),
                getStr(params, "approvalId"),
                getStr(params, "taskId"),
                getStr(params, "riskLevel"),
                getStr(params, "approvalRoute"),
                getStr(params, "timeoutMinutes"),
                getStr(params, "riskReasoning"));
        return NotificationMessage.builder()
                .template(NotificationTemplateEnum.APPROVAL_PENDING)
                .title(title)
                .content(content)
                .params(params)
                .build();
    }

    /**
     * 渲染审批超时告警模板
     *
     * <p>参数：approvalId / taskId / riskLevel / timeoutMinutes</p>
     *
     * @param params 模板参数
     * @return 通知消息
     */
    private NotificationMessage renderApprovalTimeout(Map<String, Object> params) {
        String title = "审批超时告警";
        String content = String.format("""
                ## 审批超时告警

                > 审批单已超时自动拒绝，关联任务已终止。

                **审批单编号**：%s
                **任务编号**：%s
                **风险等级**：%s
                **超时阈值**：%s 分钟

                请检查审批流程是否顺畅，必要时调整审批人配置。
                """,
                getStr(params, "approvalId"),
                getStr(params, "taskId"),
                getStr(params, "riskLevel"),
                getStr(params, "timeoutMinutes"));
        return NotificationMessage.builder()
                .template(NotificationTemplateEnum.APPROVAL_TIMEOUT)
                .title(title)
                .content(content)
                .params(params)
                .build();
    }

    /**
     * 渲染任务失败模板
     *
     * <p>参数：taskId / errorMessage / failedAt</p>
     *
     * @param params 模板参数
     * @return 通知消息
     */
    private NotificationMessage renderTaskFailed(Map<String, Object> params) {
        String title = "任务失败告警";
        String content = String.format("""
                ## 任务执行失败

                > 任务终态为 failed，请排查失败原因。

                **任务编号**：%s
                **失败时间**：%s
                **错误信息**：%s

                请前往 FinanceRPA 任务管理查看详情并重试。
                """,
                getStr(params, "taskId"),
                getStr(params, "failedAt"),
                getStr(params, "errorMessage"));
        return NotificationMessage.builder()
                .template(NotificationTemplateEnum.TASK_FAILED)
                .title(title)
                .content(content)
                .params(params)
                .build();
    }

    /**
     * 渲染 NEEDS_HUMAN 接管模板
     *
     * <p>参数：taskId / reason / llmModel / needsHumanId</p>
     *
     * @param params 模板参数
     * @return 通知消息
     */
    private NotificationMessage renderNeedsHuman(Map<String, Object> params) {
        String title = "人工接管告警";
        String content = String.format("""
                ## 需要人工接管

                > LLM 三层容错已失败，任务进入 NEEDS_HUMAN 状态，请人工介入处理。

                **接管队列编号**：%s
                **任务编号**：%s
                **触发原因**：%s
                **失败模型**：%s

                请前往 FinanceRPA 接管中心处理。
                """,
                getStr(params, "needsHumanId"),
                getStr(params, "taskId"),
                getStr(params, "reason"),
                getStr(params, "llmModel"));
        return NotificationMessage.builder()
                .template(NotificationTemplateEnum.NEEDS_HUMAN)
                .title(title)
                .content(content)
                .params(params)
                .build();
    }

    /**
     * 渲染风险等级升级模板
     *
     * <p>参数：taskId / originalRiskLevel / escalatedRiskLevel / reason</p>
     *
     * @param params 模板参数
     * @return 通知消息
     */
    private NotificationMessage renderRiskEscalation(Map<String, Object> params) {
        String title = "风险等级升级告警";
        String content = String.format("""
                ## 风险等级升级

                > LLM 判断升级了任务风险等级，请关注后续审批流程。

                **任务编号**：%s
                **原风险等级**：%s
                **升级后等级**：%s
                **升级理由**：%s
                """,
                getStr(params, "taskId"),
                getStr(params, "originalRiskLevel"),
                getStr(params, "escalatedRiskLevel"),
                getStr(params, "reason"));
        return NotificationMessage.builder()
                .template(NotificationTemplateEnum.RISK_ESCALATION)
                .title(title)
                .content(content)
                .params(params)
                .build();
    }

    /**
     * 从参数 Map 安全取字符串值
     *
     * @param params 参数 Map
     * @param key    参数键
     * @return 字符串值；缺失返回占位符
     */
    private String getStr(Map<String, Object> params, String key) {
        Object value = params.get(key);
        return value == null ? PLACEHOLDER : String.valueOf(value);
    }
}
