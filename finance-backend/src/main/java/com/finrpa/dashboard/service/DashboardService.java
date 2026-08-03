package com.finrpa.dashboard.service;

import com.finrpa.dashboard.dto.response.ApprovalStatVO;
import com.finrpa.dashboard.dto.response.BusinessLineStatVO;
import com.finrpa.dashboard.dto.response.CostStatVO;
import com.finrpa.dashboard.dto.response.ErrorTypeStatVO;
import com.finrpa.dashboard.dto.response.OverviewVO;
import com.finrpa.dashboard.dto.response.TrendsVO;

import java.util.List;

/**
 * 运营大屏服务接口（M8.1）
 *
 * <p>提供 6 类统计指标查询，所有查询走 Redis 缓存（实时指标 TTL 5min，趋势 TTL 1h）。
 * 缓存在任务进入终态时由 {@code DashboardCacheRefreshListener} 主动失效。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
public interface DashboardService {

    /**
     * 获取概览指标（任务 / 性能 / LLM / 人工 / 风险 五类汇总）
     *
     * @param orgId 组织 ID
     * @return 概览 VO
     */
    OverviewVO getOverview(Long orgId);

    /**
     * 获取趋势指标（任务量 + 成本按日聚合）
     *
     * @param orgId 组织 ID
     * @param days  天数（最近 N 天，null 时取默认值）
     * @return 趋势 VO
     */
    TrendsVO getTrends(Long orgId, Integer days);

    /**
     * 获取各业务线任务分布 + 成功率
     *
     * @param orgId 组织 ID
     * @return 业务线统计列表
     */
    List<BusinessLineStatVO> getBusinessLineStats(Long orgId);

    /**
     * 获取错误类型分布 Top 10
     *
     * @param orgId 组织 ID
     * @return 错误类型统计列表
     */
    List<ErrorTypeStatVO> getErrorTypeStats(Long orgId);

    /**
     * 获取 LLM 成本统计（含按模型维度）
     *
     * @param orgId 组织 ID
     * @return 成本统计 VO
     */
    CostStatVO getCosts(Long orgId);

    /**
     * 获取审批统计（响应时长 / 超时数）
     *
     * @param orgId 组织 ID
     * @return 审批统计 VO
     */
    ApprovalStatVO getApprovals(Long orgId);

    /**
     * 失效指定组织的全部大屏缓存（任务终态时调用）
     *
     * @param orgId 组织 ID
     */
    void invalidateCache(Long orgId);
}
