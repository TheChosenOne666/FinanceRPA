package com.finrpa.llm.service;

import com.finrpa.llm.dto.request.LlmCallLogCreateRequest;
import com.finrpa.llm.dto.request.LlmCallRecordQueryRequest;
import com.finrpa.llm.dto.request.LlmCallStatsQueryRequest;
import com.finrpa.llm.dto.response.LlmCallDailyTrendVO;
import com.finrpa.llm.dto.response.LlmCallRecordVO;
import com.finrpa.llm.dto.response.LlmCallStatsVO;
import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.List;

/**
 * LLM 调用记录服务接口
 *
 * <p>负责 LLM 调用记录的持久化与统计查询。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
public interface LlmCallLogService {

    /**
     * 创建 LLM 调用记录（Python 回调）
     *
     * @param request 调用记录创建请求
     * @return 是否创建成功
     */
    boolean createCallLog(LlmCallLogCreateRequest request);

    /**
     * 查询 LLM 调用统计（按时间/模型/任务/业务线维度筛选，含环比趋势）
     *
     * @param queryRequest 统计查询请求
     * @param orgId        组织 ID（租户隔离，外部 API 从 TenantContext 获取）
     * @return 聚合统计结果（含趋势字段）
     */
    LlmCallStatsVO getStats(LlmCallStatsQueryRequest queryRequest, Long orgId);

    /**
     * 分页查询 LLM 调用记录（P3 ai-monitoring 原型对齐：调用记录列表）
     *
     * @param queryRequest 分页查询请求（含时间/模型/任务/业务线/缓存命中筛选）
     * @param orgId        组织 ID（租户隔离）
     * @return 分页结果（按 call_time 倒序）
     */
    IPage<LlmCallRecordVO> listCallRecords(LlmCallRecordQueryRequest queryRequest, Long orgId);

    /**
     * 查询按日聚合趋势（P3 ai-monitoring 原型对齐：成本趋势 7 日折线图）
     *
     * @param queryRequest 统计查询请求（用 startTime/endTime/businessLineId 筛选）
     * @param orgId        组织 ID（租户隔离）
     * @return 按日期升序的每日聚合数据列表
     */
    List<LlmCallDailyTrendVO> getDailyTrend(LlmCallStatsQueryRequest queryRequest, Long orgId);
}

