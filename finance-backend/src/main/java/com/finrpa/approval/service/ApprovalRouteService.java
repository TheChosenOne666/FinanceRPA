package com.finrpa.approval.service;

import com.finrpa.approval.enums.ApprovalRouteEnum;

/**
 * 审批路由服务（M6.3）
 *
 * <p>按风险等级路由到对应的审批流程：
 * <ul>
 *   <li>low / medium → auto（自动通过，无需人工审批）</li>
 *   <li>high → department（部门审批）</li>
 *   <li>critical → compliance（合规审计部审批）</li>
 * </ul>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
public interface ApprovalRouteService {

    /**
     * 根据风险等级获取审批路由
     *
     * @param riskLevel 风险等级：low / medium / high / critical
     * @return 审批路由枚举
     */
    ApprovalRouteEnum routeByRiskLevel(String riskLevel);

    /**
     * 判断指定风险等级是否需要人工审批
     *
     * @param riskLevel 风险等级
     * @return true-需要人工审批（high / critical）
     */
    boolean needsHumanApproval(String riskLevel);
}
