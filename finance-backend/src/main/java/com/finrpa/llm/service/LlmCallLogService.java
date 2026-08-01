package com.finrpa.llm.service;

import com.finrpa.llm.dto.request.LlmCallLogCreateRequest;
import com.finrpa.llm.dto.request.LlmCallStatsQueryRequest;
import com.finrpa.llm.dto.response.LlmCallStatsVO;

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
     * 查询 LLM 调用统计（按时间/模型/任务维度筛选）
     *
     * @param queryRequest 统计查询请求
     * @param orgId        组织 ID（租户隔离，外部 API 从 TenantContext 获取）
     * @return 聚合统计结果
     */
    LlmCallStatsVO getStats(LlmCallStatsQueryRequest queryRequest, Long orgId);
}
