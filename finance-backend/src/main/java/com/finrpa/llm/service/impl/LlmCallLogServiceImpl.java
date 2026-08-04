package com.finrpa.llm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.finrpa.agent.entity.AgentTaskEO;
import com.finrpa.agent.mapper.AgentTaskMapper;
import com.finrpa.common.exception.ThrowUtils;
import com.finrpa.common.response.ErrorCode;
import com.finrpa.llm.constant.LlmConstant;
import com.finrpa.llm.dto.request.LlmCallLogCreateRequest;
import com.finrpa.llm.dto.request.LlmCallRecordQueryRequest;
import com.finrpa.llm.dto.request.LlmCallStatsQueryRequest;
import com.finrpa.llm.dto.response.LlmCallDailyTrendVO;
import com.finrpa.llm.dto.response.LlmCallRecordVO;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    /** 任务 Mapper（用于调用记录列表关联查询任务标题） */
    @Resource
    private AgentTaskMapper agentTaskMapper;

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

        // 3. 解析 taskId / orgId / businessLineId（Python 侧为字符串）
        callLog.setTaskId(parseLong(request.getTaskId()));
        callLog.setOrgId(parseLong(request.getOrgId()));
        callLog.setBusinessLineId(parseLong(request.getBusinessLineId()));

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

    // region 统计查询（含趋势）

    /**
     * 查询 LLM 调用统计（按时间/模型/任务/业务线维度筛选，含环比趋势）
     *
     * <p>趋势计算：若 queryRequest 同时提供 startTime/endTime，自动取等长的上一周期对比；
     * 若未提供时间范围，趋势字段为 null。</p>
     *
     * @param queryRequest 统计查询请求
     * @param orgId        组织 ID（租户隔离）
     * @return 聚合统计结果（含趋势字段）
     */
    @Override
    public LlmCallStatsVO getStats(LlmCallStatsQueryRequest queryRequest, Long orgId) {
        // 1. 当前周期查询
        QueryWrapper<LlmCallLogEO> currentWrapper = buildStatsWrapper(queryRequest, orgId);
        List<LlmCallLogEO> currentLogs = llmCallLogMapper.selectList(currentWrapper);
        LlmCallStatsVO stats = aggregateStats(currentLogs);

        // 2. 计算环比趋势（仅当提供完整时间范围时）
        if (queryRequest != null && queryRequest.getStartTime() != null && queryRequest.getEndTime() != null) {
            long durationMs = queryRequest.getEndTime().getTime() - queryRequest.getStartTime().getTime();
            Timestamp prevStart = new Timestamp(queryRequest.getStartTime().getTime() - durationMs);
            Timestamp prevEnd = queryRequest.getStartTime();
            LlmCallStatsQueryRequest prevQuery = new LlmCallStatsQueryRequest();
            prevQuery.setStartTime(prevStart);
            prevQuery.setEndTime(prevEnd);
            prevQuery.setModel(queryRequest.getModel());
            prevQuery.setTaskId(queryRequest.getTaskId());
            prevQuery.setBusinessLineId(queryRequest.getBusinessLineId());

            QueryWrapper<LlmCallLogEO> prevWrapper = buildStatsWrapper(prevQuery, orgId);
            List<LlmCallLogEO> prevLogs = llmCallLogMapper.selectList(prevWrapper);
            LlmCallStatsVO prevStats = aggregateStats(prevLogs);

            stats.setTotalCallsTrendPct(calcTrendPct(prevStats.getTotalCalls(), stats.getTotalCalls()));
            stats.setTotalCostTrendPct(calcTrendPct(
                    prevStats.getTotalCost() != null ? prevStats.getTotalCost().doubleValue() : 0.0,
                    stats.getTotalCost() != null ? stats.getTotalCost().doubleValue() : 0.0));
            // 缓存命中率趋势用百分点差值
            stats.setCacheHitRateTrendPct(
                    Math.round((stats.getCacheHitRate() - prevStats.getCacheHitRate()) * 10000) / 100.0);
            stats.setAvgDurationTrendPct(calcTrendPct(prevStats.getAvgDurationMs(), stats.getAvgDurationMs()));
        }

        return stats;
    }

    // endregion

    // region 调用记录分页查询

    /**
     * 分页查询 LLM 调用记录（按 call_time 倒序）
     *
     * @param queryRequest 分页查询请求
     * @param orgId        组织 ID（租户隔离）
     * @return 分页结果
     */
    @Override
    public IPage<LlmCallRecordVO> listCallRecords(LlmCallRecordQueryRequest queryRequest, Long orgId) {
        ThrowUtils.throwIf(queryRequest == null, ErrorCode.PARAMS_ERROR, "查询请求不能为空");
        int current = queryRequest.getCurrent();
        int pageSize = queryRequest.getPageSize();
        ThrowUtils.throwIf(pageSize > 100, ErrorCode.PARAMS_ERROR, "单页大小不能超过 100");

        // 1. 构建查询条件
        QueryWrapper<LlmCallLogEO> wrapper = new QueryWrapper<>();
        if (orgId != null) {
            wrapper.eq("org_id", orgId);
        }
        if (queryRequest.getStartTime() != null) {
            wrapper.ge("call_time", queryRequest.getStartTime());
        }
        if (queryRequest.getEndTime() != null) {
            wrapper.le("call_time", queryRequest.getEndTime());
        }
        if (queryRequest.getModel() != null && !queryRequest.getModel().isBlank()) {
            wrapper.eq("model", queryRequest.getModel());
        }
        if (queryRequest.getTaskId() != null) {
            wrapper.eq("task_id", queryRequest.getTaskId());
        }
        if (queryRequest.getBusinessLineId() != null) {
            wrapper.eq("business_line_id", queryRequest.getBusinessLineId());
        }
        if (queryRequest.getCacheHit() != null) {
            wrapper.eq("cache_hit", queryRequest.getCacheHit());
        }
        wrapper.orderByDesc("call_time");

        // 2. 分页查询
        Page<LlmCallLogEO> page = new Page<>(current, pageSize);
        IPage<LlmCallLogEO> eoPage = llmCallLogMapper.selectPage(page, wrapper);

        // 3. 转 VO
        Page<LlmCallRecordVO> voPage = new Page<>(current, pageSize);
        voPage.setTotal(eoPage.getTotal());

        List<LlmCallLogEO> records = eoPage.getRecords();
        if (records.isEmpty()) {
            voPage.setRecords(new ArrayList<>());
            return voPage;
        }

        // 4. 批量查询任务标题（避免 N+1）
        List<Long> taskIds = records.stream()
                .map(LlmCallLogEO::getTaskId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, String> taskTitleMap = new LinkedHashMap<>();
        if (!taskIds.isEmpty()) {
            QueryWrapper<AgentTaskEO> taskWrapper = new QueryWrapper<>();
            taskWrapper.in("task_id", taskIds);
            List<AgentTaskEO> tasks = agentTaskMapper.selectList(taskWrapper);
            for (AgentTaskEO task : tasks) {
                taskTitleMap.put(task.getTaskId(), task.getGoal());
            }
        }

        // 5. 组装 VO
        List<LlmCallRecordVO> voList = records.stream().map(eo -> {
            LlmCallRecordVO vo = new LlmCallRecordVO();
            vo.setCallId(eo.getCallId());
            vo.setTaskId(eo.getTaskId());
            vo.setTaskTitle(eo.getTaskId() != null ? taskTitleMap.get(eo.getTaskId()) : null);
            vo.setModel(eo.getModel());
            vo.setContextName(eo.getContextName());
            vo.setSuccess(eo.getSuccess());
            vo.setCacheHit(eo.getCacheHit());
            vo.setCost(eo.getCost());
            vo.setDurationMs(eo.getDurationMs());
            vo.setCallTime(eo.getCallTime());
            return vo;
        }).collect(Collectors.toList());
        voPage.setRecords(voList);

        return voPage;
    }

    // endregion

    // region 按日聚合趋势

    /**
     * 查询按日聚合趋势（按日期升序返回）
     *
     * <p>若未提供时间范围，默认查最近 7 天。</p>
     *
     * @param queryRequest 统计查询请求（用 startTime/endTime/businessLineId 筛选）
     * @param orgId        组织 ID（租户隔离）
     * @return 按日期升序的每日聚合数据列表
     */
    @Override
    public List<LlmCallDailyTrendVO> getDailyTrend(LlmCallStatsQueryRequest queryRequest, Long orgId) {
        // 1. 默认时间范围：最近 7 天
        Timestamp startTime = queryRequest != null ? queryRequest.getStartTime() : null;
        Timestamp endTime = queryRequest != null ? queryRequest.getEndTime() : null;
        if (startTime == null || endTime == null) {
            endTime = Timestamp.valueOf(LocalDateTime.now());
            startTime = Timestamp.valueOf(endTime.toInstant()
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime()
                    .minusDays(6)
                    .toLocalDate()
                    .atStartOfDay());
        }

        // 2. 查询时间范围内的记录
        QueryWrapper<LlmCallLogEO> wrapper = new QueryWrapper<>();
        if (orgId != null) {
            wrapper.eq("org_id", orgId);
        }
        wrapper.ge("call_time", startTime);
        wrapper.le("call_time", endTime);
        if (queryRequest != null) {
            if (queryRequest.getModel() != null && !queryRequest.getModel().isBlank()) {
                wrapper.eq("model", queryRequest.getModel());
            }
            if (queryRequest.getBusinessLineId() != null) {
                wrapper.eq("business_line_id", queryRequest.getBusinessLineId());
            }
            if (queryRequest.getTaskId() != null) {
                wrapper.eq("task_id", queryRequest.getTaskId());
            }
        }
        List<LlmCallLogEO> logs = llmCallLogMapper.selectList(wrapper);

        // 3. 按日期分组聚合
        Map<LocalDate, List<LlmCallLogEO>> grouped = logs.stream()
                .filter(l -> l.getCallTime() != null)
                .collect(Collectors.groupingBy(
                        l -> l.getCallTime().toInstant().atZone(ZoneId.systemDefault()).toLocalDate(),
                        LinkedHashMap::new,
                        Collectors.toList()));

        // 4. 填充每一天（含无数据日）
        List<LlmCallDailyTrendVO> result = new ArrayList<>();
        LocalDate start = startTime.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        LocalDate end = endTime.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            List<LlmCallLogEO> dayLogs = grouped.getOrDefault(d, java.util.Collections.emptyList());
            LlmCallDailyTrendVO vo = new LlmCallDailyTrendVO();
            vo.setDate(d.toString());
            vo.setCalls((long) dayLogs.size());
            BigDecimal dayCost = dayLogs.stream()
                    .map(l -> l.getCost() != null ? l.getCost() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            vo.setCost(dayCost);
            double avgMs = dayLogs.stream()
                    .mapToInt(l -> l.getDurationMs() != null ? l.getDurationMs() : 0)
                    .average()
                    .orElse(0.0);
            vo.setAvgDurationMs(Math.round(avgMs * 100) / 100.0);
            result.add(vo);
        }

        return result;
    }

    // endregion

    // region 私有方法：构建统计查询条件

    /**
     * 构建统计查询 QueryWrapper（复用于当前周期 / 上一周期）
     */
    private QueryWrapper<LlmCallLogEO> buildStatsWrapper(LlmCallStatsQueryRequest queryRequest, Long orgId) {
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
            if (queryRequest.getBusinessLineId() != null) {
                wrapper.eq("business_line_id", queryRequest.getBusinessLineId());
            }
        }
        return wrapper;
    }

    // endregion

    // region 私有方法：趋势百分比计算

    /**
     * 计算环比百分比变化
     *
     * @param prev 上一周期值
     * @param curr 当前周期值
     * @return 变化百分比（正数↑增长 / 负数↓下降），上一周期为 0 时返回 null（无法计算）
     */
    private Double calcTrendPct(double prev, double curr) {
        if (prev == 0.0) {
            return curr == 0.0 ? 0.0 : null;
        }
        return Math.round((curr - prev) / prev * 10000) / 100.0;
    }

    /**
     * 计算环比百分比变化（Long 重载）
     */
    private Double calcTrendPct(long prev, long curr) {
        return calcTrendPct((double) prev, (double) curr);
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
