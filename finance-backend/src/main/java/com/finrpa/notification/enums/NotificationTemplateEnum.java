package com.finrpa.notification.enums;

import com.finrpa.notification.constant.NotificationConstant;
import lombok.Getter;

/**
 * 通知模板类型枚举（M6.6）
 *
 * <p>覆盖 5 类业务场景（对齐 system-design.md 6.10.1）：
 * <ul>
 *   <li>{@link #APPROVAL_PENDING} 审批待处理：approval:requests 发布时</li>
 *   <li>{@link #APPROVAL_TIMEOUT} 审批超时告警：超时检测触发时</li>
 *   <li>{@link #TASK_FAILED} 任务失败：任务终态为 failed 时</li>
 *   <li>{@link #NEEDS_HUMAN} NEEDS_HUMAN 接管：LLM 三层容错失败时</li>
 *   <li>{@link #RISK_ESCALATION} 风险等级升级：LLM 判断升级 risk_level 时</li>
 * </ul>
 * </p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Getter
public enum NotificationTemplateEnum {

    /** 审批待处理 */
    APPROVAL_PENDING(NotificationConstant.TEMPLATE_APPROVAL_PENDING, "审批待处理"),

    /** 审批超时告警 */
    APPROVAL_TIMEOUT(NotificationConstant.TEMPLATE_APPROVAL_TIMEOUT, "审批超时告警"),

    /** 任务失败 */
    TASK_FAILED(NotificationConstant.TEMPLATE_TASK_FAILED, "任务失败"),

    /** NEEDS_HUMAN 接管 */
    NEEDS_HUMAN(NotificationConstant.TEMPLATE_NEEDS_HUMAN, "NEEDS_HUMAN 接管"),

    /** 风险等级升级 */
    RISK_ESCALATION(NotificationConstant.TEMPLATE_RISK_ESCALATION, "风险等级升级");

    /** 模板类型值（与 NotificationConstant 对齐） */
    private final String value;

    /** 模板中文名 */
    private final String label;

    /**
     * 构造模板枚举
     *
     * @param value 模板类型值
     * @param label 模板中文名
     */
    NotificationTemplateEnum(String value, String label) {
        this.value = value;
        this.label = label;
    }

    /**
     * 根据 value 解析模板枚举
     *
     * @param value 模板类型值
     * @return 模板枚举；未匹配返回 null
     */
    public static NotificationTemplateEnum getEnumByValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (NotificationTemplateEnum template : NotificationTemplateEnum.values()) {
            if (template.value.equals(value)) {
                return template;
            }
        }
        return null;
    }
}
