package com.finrpa.approval.service.impl;

import com.finrpa.approval.enums.ApprovalRouteEnum;
import com.finrpa.approval.service.ApprovalRouteService;
import org.springframework.stereotype.Service;

/**
 * 审批路由服务实现（M6.3）
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Service
public class ApprovalRouteServiceImpl implements ApprovalRouteService {

    /**
     * 根据风险等级获取审批路由
     *
     * @param riskLevel 风险等级：low / medium / high / critical
     * @return 审批路由枚举
     */
    @Override
    public ApprovalRouteEnum routeByRiskLevel(String riskLevel) {
        return ApprovalRouteEnum.fromRiskLevel(riskLevel);
    }

    /**
     * 判断指定风险等级是否需要人工审批
     *
     * @param riskLevel 风险等级
     * @return true-需要人工审批（high / critical）
     */
    @Override
    public boolean needsHumanApproval(String riskLevel) {
        ApprovalRouteEnum route = routeByRiskLevel(riskLevel);
        return ApprovalRouteEnum.needsHumanApproval(route.getValue());
    }
}
