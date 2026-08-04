package com.finrpa.approval.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.finrpa.approval.dto.request.ApprovalRouteConfigAddRequest;
import com.finrpa.approval.dto.request.ApprovalRouteConfigQueryRequest;
import com.finrpa.approval.dto.request.ApprovalRouteConfigUpdateRequest;
import com.finrpa.approval.dto.response.ApprovalRouteConfigVO;

/**
 * 审批人映射配置服务接口（P1 RSK-3）
 *
 * <p>提供「风险等级 × 业务线 → 审批人」映射规则的 CRUD 与查询能力。
 * 审批服务（{@link ApprovalService}）在创建审批单时通过
 * {@link #getApproverUserId(Long, String, Long)} 查找审批人。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
public interface ApprovalRouteConfigService {

    /**
     * 分页查询审批人映射配置（按当前请求组织过滤）
     *
     * @param queryRequest 查询请求（含分页 + 筛选参数）
     * @param orgId        组织业务 ID
     * @return 分页结果
     */
    IPage<ApprovalRouteConfigVO> listConfigs(ApprovalRouteConfigQueryRequest queryRequest, Long orgId);

    /**
     * 根据风险等级 + 业务线查找审批人用户 ID
     *
     * <p>查找顺序：
     * <ol>
     *   <li>精确匹配：{@code (orgId, riskLevel, businessLineId)}</li>
     *   <li>默认路由：{@code (orgId, riskLevel, businessLineId IS NULL)}</li>
     * </ol>
     * 仍找不到时返回 null（审批单 approver_id 留空，由审批中心手动认领）。</p>
     *
     * @param orgId           组织业务 ID
     * @param riskLevel       风险等级
     * @param businessLineId  业务线业务 ID（可空）
     * @return 审批人用户业务 ID；找不到时返回 null
     */
    Long getApproverUserId(Long orgId, String riskLevel, Long businessLineId);

    /**
     * 新增审批人映射配置
     *
     * @param orgId   组织业务 ID
     * @param request 新增请求
     * @return 新建的配置业务 ID
     */
    Long addConfig(Long orgId, ApprovalRouteConfigAddRequest request);

    /**
     * 更新审批人映射配置
     *
     * @param configId 配置业务 ID
     * @param request  更新请求
     * @return 是否更新成功
     */
    boolean updateConfig(Long configId, ApprovalRouteConfigUpdateRequest request);

    /**
     * 删除审批人映射配置
     *
     * @param configId 配置业务 ID
     * @return 是否删除成功
     */
    boolean deleteConfig(Long configId);
}
