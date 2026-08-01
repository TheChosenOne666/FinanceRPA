package com.finrpa.llm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.finrpa.common.exception.ThrowUtils;
import com.finrpa.common.response.ErrorCode;
import com.finrpa.llm.constant.LlmConstant;
import com.finrpa.llm.dto.request.LlmCallLogCreateRequest;
import com.finrpa.llm.dto.request.LlmCallStatsQueryRequest;
import com.finrpa.llm.dto.response.LlmCallStatsVO;
import com.finrpa.llm.dto.response.ModelStatsVO;
import com.finrpa.llm.entity.LlmCallLogEO;
import com.finrpa.llm.mapper.LlmCallLogMapper;
import com.finrpa.llm.service.LlmCallLogService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * LLM 调用记录服务实现
 *
 * <p>实现调用记录持久化、成本计算与统计聚合。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Slf4j
@Service
public class LlmCallLogServiceImpl implements LlmCallLogService {

    /** LLM 调用记录 Mapper */
    @Resource
    private LlmCallLogMapper llmCallLogMapper;

    // region 创建调用记录

    /**
     * 创建 LLM 调用记录（Python 回调）
     *
     * @param request 调用记录创建请求
     * @return 是否创建成功
     */
    @Override
    public boolean createCallLog(LlmCallLogCreateRequest request) {
        // 1. 参数校验
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR, "调用记录请求不能为空");
        ThrowUtils.throwIf(request.getModel() == null || request.getModel().isBlank(),
                ErrorCode.PARAMS_ERROR, "模型名不能为空");

        // 2. 构建实体
        LlmCallLogEO callLog = new LlmCallLogEO();
        callLog.setModel(request.getModel());
        callLog.setContextName(request.getContextName() != null && !request.getContextName().isBlank()
                ? request.getContextName() : LlmConstant.DEFAULT_CONTEXT);
        callLog.setRetryAttempt(request.getRetryAttempt() != null ? request.getRetryAttempt() : 0);
        callLog.setSuccess(request.getSuccess() != null ? request.getSuccess() : false);
        callLog.setErrorMessage(request.getErrorMessage());
        callLog.setDurationMs(request.getDurationMs() != null ? request.getDurationMs() : 0);
        callLog.setPromptTokens(request.getPromptTokens());
        callLog.setCompletionTokens(request.getCompletionTokens());
        callLog.setTotalTokens(request.getTotalTokens());
        callLog.setCacheHit(request.getCacheHit() != null ? request.getCacheHit() : false);

        // 3. 解析 taskId / orgId（Python 侧为字符串）
        callLog.setTaskId(parseLong(request.getTaskId()));
        callLog.setOrgId(parseLong(request.getOrgId()));

        // 4. 解析调用时间戳
        callLog.setCallTime(parseTimestamp(request.getTimestamp()));

        // 5. 计算成本
        callLog.setCost(calculateCost(request.getModel(),
                request.getPromptTokens(), request.getCompletionTokens()));

        // 6. 保存
        int rows = llmCallLogMapper.insert(callLog);
        ThrowUtils.throwIf(rows <= 0, ErrorCode.OPERATION_ERROR, "LLM 调用记录保存失败");

        log.info("LLM 调用记录保存成功: callId={}, taskId={}, model={}, success={}, cost=${}",
                callLog.getCallId(), callLog.getTaskId(),
                callLog.getModel(), callLog.getSuccess(), callLog.getCost());
        return true;
    }

    // endregion

    // region 统计查询

    /**
     * 查询 LLM 调用统计（按时间/模型/任务维度筛选）
     *
     * @param queryRequest 统计查询请求
     * @param orgId        组织 ID（租户隔离）
     * @return 聚合统计结果
     */
    @Override
    public LlmCallStatsVO getStats(LlmCallStatsQueryRequest queryRequest, Long orgId) {
        // 1. 构建查询条件
        QueryWrapper<LlmCallLogEO> wrapper = new QueryWrapper<>();
        if (orgId != null) {
            wrapper.eq("org_id", orgId);
        }
        if (queryRequest != null) {
            if (queryRequest.getStartTime() != null) {
                wrapper.ge("create_time", queryRequest.getStartTime());
            }
            if (queryRequest.getEndTime() != null) {
                wrapper.le("create_time", queryRequest.getEndTime());
            }
            if (queryRequest.getModel() != null && !queryRequest.getModel().isBlank()) {
                wrapper.eq("model", queryRequest.getModel());
            }
            if (queryRequest.getTaskId() != null) {
                wrapper.eq("task_id", queryRequest.getTaskId());
            }
        }

        // 2. 查询记录
        List<LlmCallLogEO> logs = llmCallLogMapper.selectList(wrapper);

        // 3. 聚合统计
        return aggregateStats(logs);
    }

    // endregion

    // region 私有方法：成本计算

    /**
     * 计算单次调用成本
     *
     * <p>按模型 token 单价计算：cost = promptTokens * inputPrice / 1M + completionTokens * outputPrice / 1M</p>
     *
     * @param model            模型名
     * @param promptTokens     prompt token 数
     * @param completionTokens completion token 数
     * @return 成本（美元），保留 6 位小数
     */
    private BigDecimal calculateCost(String model, Integer promptTokens, Integer completionTokens) {
        double[] pricing = LlmConstant.MODEL_PRICING.getOrDefault(model, LlmConstant.DEFAULT_PRICING);
        double inputPrice = pricing[0];
        double outputPrice = pricing[1];

        int prompt = promptTokens != null ? promptTokens : 0;
        int completion = completionTokens != null ? completionTokens : 0;

        double cost = (prompt * inputPrice + completion * outputPrice) / LlmConstant.PRICING_DIVISOR;
        return BigDecimal.valueOf(cost).setScale(6, RoundingMode.HALF_UP);
    }

    // endregion

    // region 私有方法：聚合统计

    /**
     * 将调用记录列表聚合为统计 VO
     *
     * @param logs 调用记录列表
     * @return 聚合统计结果
     */
    private LlmCallStatsVO aggregateStats(List<LlmCallLogEO> logs) {
        LlmCallStatsVO stats = new LlmCallStatsVO();

        if (logs == null || logs.isEmpty()) {
            stats.setTotalCalls(0L);
            stats.setSuccessCalls(0L);
            stats.setFailedCalls(0L);
            stats.setCacheHitCalls(0L);
            stats.setCacheHitRate(0.0);
            stats.setTotalPromptTokens(0L);
            stats.setTotalCompletionTokens(0L);
            stats.setTotalTokens(0L);
            stats.setTotalCost(BigDecimal.ZERO);
            stats.setAvgDurationMs(0.0);
            stats.setModelStats(new ArrayList<>());
            return stats;
        }

        long totalCalls = logs.size();
        long successCalls = logs.stream().filter(l -> Boolean.TRUE.equals(l.getSuccess())).count();
        long failedCalls = totalCalls - successCalls;
        long cacheHitCalls = logs.stream().filter(l -> Boolean.TRUE.equals(l.getCacheHit())).count();
        double cacheHitRate = totalCalls > 0 ? (double) cacheHitCalls / totalCalls : 0.0;

        long totalPromptTokens = logs.stream()
                .mapToLong(l -> l.getPromptTokens() != null ? l.getPromptTokens() : 0)
                .sum();
        long totalCompletionTokens = logs.stream()
                .mapToLong(l -> l.getCompletionTokens() != null ? l.getCompletionTokens() : 0)
                .sum();
        long totalTokens = logs.stream()
                .mapToLong(l -> l.getTotalTokens() != null ? l.getTotalTokens() : 0)
                .sum();

        BigDecimal totalCost = logs.stream()
                .map(l -> l.getCost() != null ? l.getCost() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        double avgDurationMs = logs.stream()
                .mapToInt(l -> l.getDurationMs() != null ? l.getDurationMs() : 0)
                .average()
                .orElse(0.0);

        // 按模型维度聚合
        Map<String, ModelStatsVO> modelMap = new LinkedHashMap<>();
        for (LlmCallLogEO log : logs) {
            String model = log.getModel();
            ModelStatsVO modelStat = modelMap.computeIfAbsent(model, k -> new ModelStatsVO(k, 0L, 0L, 0L, BigDecimal.ZERO));
            modelStat.setCalls(modelStat.getCalls() + 1);
            if (Boolean.TRUE.equals(log.getSuccess())) {
                modelStat.setSuccessCalls(modelStat.getSuccessCalls() + 1);
            }
            modelStat.setTotalTokens(modelStat.getTotalTokens()
                    + (log.getTotalTokens() != null ? log.getTotalTokens() : 0));
            modelStat.setCost(modelStat.getCost().add(log.getCost() != null ? log.getCost() : BigDecimal.ZERO));
        }

        stats.setTotalCalls(totalCalls);
        stats.setSuccessCalls(successCalls);
        stats.setFailedCalls(failedCalls);
        stats.setCacheHitCalls(cacheHitCalls);
        stats.setCacheHitRate(Math.round(cacheHitRate * 10000) / 10000.0);
        stats.setTotalPromptTokens(totalPromptTokens);
        stats.setTotalCompletionTokens(totalCompletionTokens);
        stats.setTotalTokens(totalTokens);
        stats.setTotalCost(totalCost);
        stats.setAvgDurationMs(Math.round(avgDurationMs * 100) / 100.0);
        stats.setModelStats(new ArrayList<>(modelMap.values()));

        return stats;
    }

    // endregion

    // region 私有方法：类型解析

    /**
     * 解析字符串为 Long（兼容 null / 空串 / 非数字）
     *
     * @param value 字符串值
     * @return Long 值，解析失败返回 null
     */
    private Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            log.warn("解析 Long 失败: {}", value);
            return null;
        }
    }

    /**
     * 解析 ISO 8601 时间戳字符串为 Timestamp
     *
     * <p>Python {@code datetime.utcnow().isoformat()} 格式如 {@code 2026-08-01T12:34:56.789012}。</p>
     *
     * @param timestamp ISO 8601 时间戳字符串
     * @return Timestamp 对象，解析失败返回 null
     */
    private Timestamp parseTimestamp(String timestamp) {
        if (timestamp == null || timestamp.isBlank()) {
            return null;
        }
        try {
            LocalDateTime ldt = LocalDateTime.parse(timestamp.trim());
            return Timestamp.valueOf(ldt);
        } catch (Exception e) {
            log.warn("解析时间戳失败: {}", timestamp);
            return null;
        }
    }

    // endregion
}
