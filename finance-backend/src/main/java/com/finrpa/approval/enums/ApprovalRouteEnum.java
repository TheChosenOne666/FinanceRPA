package com.finrpa.approval.enums;

import com.finrpa.approval.constant.ApprovalConstant;

/**
 * 审批路由枚举（M6.3）
 *
 * <p>按风险等级路由：
 * <ul>
 *   <li>low / medium → auto（自动通过，无需人工审批）</li>
 *   <li>high → department（部门审批）</li>
 *   <li>critical → compliance（合规审计部审批）</li>
 * </ul>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
public enum ApprovalRouteEnum {

    /** 自动通过（low / medium 风险） */
    AUTO(ApprovalConstant.APPROVAL_ROUTE_AUTO),

    /** 部门审批（high 风险） */
    DEPARTMENT(ApprovalConstant.APPROVAL_ROUTE_DEPARTMENT),

    /** 合规审计部审批（critical 风险） */
    COMPLIANCE(ApprovalConstant.APPROVAL_ROUTE_COMPLIANCE);

    /** 路由值 */
    private final String value;

    ApprovalRouteEnum(String value) {
        this.value = value;
    }

    /**
     * 获取路由值
     *
     * @return 路由值
     */
    public String getValue() {
        return value;
    }

    /**
     * 根据风险等级获取审批路由
     *
     * @param riskLevel 风险等级：low / medium / high / critical
     * @return 审批路由枚举
     */
    public static ApprovalRouteEnum fromRiskLevel(String riskLevel) {
        if (riskLevel == null) {
            return AUTO;
        }
        return switch (riskLevel) {
            case "high" -> DEPARTMENT;
            case "critical" -> COMPLIANCE;
            default -> AUTO;
        };
    }

    /**
     * 判断是否需要人工审批
     *
     * @param value 路由值
     * @return true-需要人工审批（department / compliance）
     */
    public static boolean needsHumanApproval(String value) {
        return ApprovalConstant.APPROVAL_ROUTE_DEPARTMENT.equals(value)
                || ApprovalConstant.APPROVAL_ROUTE_COMPLIANCE.equals(value);
    }
}
