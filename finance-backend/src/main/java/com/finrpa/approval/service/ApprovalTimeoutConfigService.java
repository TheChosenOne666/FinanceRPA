package com.finrpa.approval.service;

import com.finrpa.approval.dto.request.ApprovalTimeoutConfigUpdateRequest;
import com.finrpa.approval.dto.response.ApprovalTimeoutConfigVO;

import java.util.List;

/**
 * 审批超时阈值配置服务接口（P1 RSK-1）
 *
 * <p>提供按风险等级读取 / 更新审批超时分钟数的能力。
 * 审批服务（{@link com.finrpa.approval.service.ApprovalService}）在创建审批单时
 * 通过 {@link #getTimeoutMinutesByRiskLevel(String)} 读取配置，
 * 配置缺失或被禁用时回退到 {@link com.finrpa.approval.constant.ApprovalConstant} 默认值。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
public interface ApprovalTimeoutConfigService {

    /**
     * 查询全部超时配置（设置页展示用）
     *
     * @return 超时配置列表
     */
    List<ApprovalTimeoutConfigVO> listAll();

    /**
     * 根据风险等级获取超时分钟数
     *
     * <p>配置不存在或被禁用时，回退到 {@link com.finrpa.approval.constant.ApprovalConstant} 默认值：
     * high=30 / critical=60 / 其他=30。</p>
     *
     * @param riskLevel 风险等级
     * @return 超时分钟数
     */
    long getTimeoutMinutesByRiskLevel(String riskLevel);

    /**
     * 更新指定风险等级的超时配置
     *
     * @param riskLevel 风险等级（high / critical）
     * @param request   更新请求
     * @return 更新后的配置 VO
     */
    ApprovalTimeoutConfigVO updateConfig(String riskLevel, ApprovalTimeoutConfigUpdateRequest request);
}
