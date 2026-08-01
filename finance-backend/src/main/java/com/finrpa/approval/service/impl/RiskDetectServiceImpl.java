package com.finrpa.approval.service.impl;

import com.finrpa.ai.client.AiServiceClient;
import com.finrpa.approval.constant.ApprovalConstant;
import com.finrpa.approval.dto.request.RiskDetectRequest;
import com.finrpa.approval.dto.request.RiskJudgeRequest;
import com.finrpa.approval.dto.response.RiskDetectResultVO;
import com.finrpa.approval.dto.response.RiskJudgeResponse;
import com.finrpa.approval.entity.RiskKeywordEO;
import com.finrpa.approval.service.RiskDetectService;
import com.finrpa.approval.service.RiskKeywordService;
import com.finrpa.common.exception.ThrowUtils;
import com.finrpa.common.response.ErrorCode;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 风险检测服务实现（阶段 1：关键词预筛）
 *
 * <p>实现关键词匹配 + 金额正则检测 + 风险等级判定。
 * 命中中高风险时调 Python LLM 二次判断（M6.2 实现，M6.1 预留接口）。</p>
 *
 * <p>风险等级判定规则：
 * <ul>
 *   <li>critical —— 同时命中高风险操作 + 敏感数据</li>
 *   <li>high —— 命中高风险关键词 或 大额（超过行业阈值）</li>
 *   <li>medium —— 命中中风险关键词 或 小额</li>
 *   <li>low —— 无命中</li>
 * </ul>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Slf4j
@Service
public class RiskDetectServiceImpl implements RiskDetectService {

    // region 金额正则（支持 ¥ / ￥ / $ / 元 / 万元 / 万）

    /** 人民币金额：￥50,000.00 / ¥10000 / 人民币：50000 */
    private static final Pattern CNY_PATTERN = Pattern.compile(
            "(?:￥|¥|人民币)\\s*[:：]?\\s*([\\d,]+(?:\\.\\d+)?)"
    );

    /** 美元金额：$1,000,000 / USD 5000 */
    private static final Pattern USD_PATTERN = Pattern.compile(
            "(?:\\$|USD)\\s*([\\d,]+(?:\\.\\d+)?)"
    );

    /** 中文金额带单位：50000元 / 100万元 / 5万 */
    private static final Pattern CN_UNIT_PATTERN = Pattern.compile(
            "([\\d,]+(?:\\.\\d+)?)\\s*(万元|万|元)"
    );

    /** 金额前缀关键词：金额：50000 / amount: 10000 */
    private static final Pattern AMOUNT_KEYWORD_PATTERN = Pattern.compile(
            "(?:金额|amount|总额|转账金额)\\s*[:：]?\\s*([\\d,]+(?:\\.\\d+)?)",
            Pattern.CASE_INSENSITIVE
    );

    // endregion

    /** 风险关键词管理服务 */
    @Resource
    private RiskKeywordService riskKeywordService;

    /** Python AI 服务客户端（M6.2 LLM 风险判断） */
    @Resource
    private AiServiceClient aiServiceClient;

    // region 关键词预筛

    /**
     * 关键词预筛（阶段 1，不调 LLM）
     *
     * @param request 预筛请求
     * @return 预筛结果
     */
    @Override
    public RiskDetectResultVO detect(RiskDetectRequest request) {
        long startTime = System.currentTimeMillis();

        // 1. 参数校验
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR, "预筛请求不能为空");
        ThrowUtils.throwIf((request.getGoal() == null || request.getGoal().isBlank())
                        && (request.getParams() == null || request.getParams().isEmpty()),
                ErrorCode.PARAMS_ERROR, "任务目标和参数不能同时为空");

        // 2. 拼接待检测文本（goal + params 值）
        String text = buildDetectText(request);
        String industry = request.getIndustry();

        // 3. 加载关键词库（按行业过滤，industry 为空时加载全部）
        List<RiskKeywordEO> keywords = riskKeywordService.listEnabledKeywords(industry);
        log.debug("加载关键词: industry={}, count={}, textLength={}", industry, keywords.size(), text.length());

        // 4. 关键词匹配
        List<RiskDetectResultVO.HitKeyword> hitKeywords = matchKeywords(text, keywords);

        // 5. 金额检测
        List<RiskDetectResultVO.AmountMatch> amountMatches = detectAmounts(text);

        // 6. 风险等级判定
        RiskDetectResultVO result = buildResult(request, hitKeywords, amountMatches);
        result.setDurationMs(System.currentTimeMillis() - startTime);

        log.info("风险预筛完成: taskId={}, industry={}, hitKeywords={}, amounts={}, riskLevel={}, action={}, cost={}ms",
                request.getTaskId(), industry, hitKeywords.size(), amountMatches.size(),
                result.getSuggestedRiskLevel(), result.getSuggestedAction(), result.getDurationMs());
        return result;
    }

    // endregion

    // region 预筛 + LLM 二次判断

    /**
     * 关键词预筛 + LLM 二次判断（阶段 1 + 阶段 2）
     *
     * <p>M6.1 阶段 Python 端尚未实现 {@code POST /api/v1/ai/risk/judge}，调用失败时回退使用预筛结果。</p>
     *
     * @param request 预筛请求
     * @return LLM 判断响应（M6.1 阶段返回 null 表示未调用 LLM）
     */
    @Override
    public RiskJudgeResponse detectAndJudge(RiskDetectRequest request) {
        // 1. 阶段 1：关键词预筛
        RiskDetectResultVO detectResult = detect(request);

        // 2. 判定是否需要调 LLM
        if (!ApprovalConstant.ACTION_JUDGE.equals(detectResult.getSuggestedAction())) {
            log.info("预筛建议动作为 proceed，跳过 LLM 二次判断: taskId={}, riskLevel={}",
                    request.getTaskId(), detectResult.getSuggestedRiskLevel());
            return null;
        }

        // 3. 构建 LLM 判断请求
        RiskJudgeRequest judgeRequest = buildJudgeRequest(request, detectResult);

        // 4. 调用 Python LLM 二次判断（M6.2 实现）
        try {
            log.info("调用 Python LLM 风险二次判断: taskId={}, preScreenRiskLevel={}",
                    request.getTaskId(), detectResult.getSuggestedRiskLevel());
            RiskJudgeResponse response = aiServiceClient.judgeRisk(judgeRequest);
            log.info("Python LLM 风险判断完成: taskId={}, finalRiskLevel={}, route={}",
                    request.getTaskId(),
                    response != null ? response.getFinalRiskLevel() : null,
                    response != null ? response.getApprovalRoute() : null);
            return response;
        } catch (Exception e) {
            // M6.1 阶段 Python 端未实现此接口，调用失败时回退使用预筛结果
            log.warn("Python LLM 风险判断调用失败，回退使用预筛结果: taskId={}, error={}",
                    request.getTaskId(), e.getMessage());
            return null;
        }
    }

    // endregion

    // region 私有方法：文本构建

    /**
     * 拼接待检测文本（goal + params 值）
     *
     * @param request 预筛请求
     * @return 待检测文本
     */
    private String buildDetectText(RiskDetectRequest request) {
        StringBuilder sb = new StringBuilder();
        if (request.getGoal() != null) {
            sb.append(request.getGoal());
        }
        if (request.getParams() != null) {
            for (Object value : request.getParams().values()) {
                if (value != null) {
                    sb.append(' ').append(value.toString());
                }
            }
        }
        return sb.toString();
    }

    // endregion

    // region 私有方法：关键词匹配

    /**
     * 关键词匹配（遍历关键词库，逐一在文本中查找）
     *
     * @param text     待检测文本
     * @param keywords 关键词库
     * @return 命中关键词列表
     */
    private List<RiskDetectResultVO.HitKeyword> matchKeywords(String text, List<RiskKeywordEO> keywords) {
        List<RiskDetectResultVO.HitKeyword> hits = new ArrayList<>();
        if (text == null || text.isEmpty() || keywords == null || keywords.isEmpty()) {
            return hits;
        }

        for (RiskKeywordEO kw : keywords) {
            String keyword = kw.getKeyword();
            if (keyword == null || keyword.isEmpty()) {
                continue;
            }
            int pos = text.indexOf(keyword);
            if (pos >= 0) {
                RiskDetectResultVO.HitKeyword hit = new RiskDetectResultVO.HitKeyword(
                        keyword, kw.getIndustry(), kw.getCategory(), kw.getRiskType(), kw.getDescription()
                );
                hit.setPosition(pos);
                hits.add(hit);
            }
        }
        return hits;
    }

    // endregion

    // region 私有方法：金额检测

    /**
     * 金额检测（识别文本中的金额并解析为元）
     *
     * @param text 待检测文本
     * @return 金额匹配列表
     */
    private List<RiskDetectResultVO.AmountMatch> detectAmounts(String text) {
        List<RiskDetectResultVO.AmountMatch> matches = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return matches;
        }

        // 1. 人民币金额
        collectCnyAmounts(text, matches);
        // 2. 美元金额（按 7.2 汇率折算为人民币）
        collectUsdAmounts(text, matches);
        // 3. 中文金额带单位
        collectCnUnitAmounts(text, matches);
        // 4. 金额前缀关键词
        collectAmountKeywordAmounts(text, matches);

        return matches;
    }

    /**
     * 解析人民币金额（￥ / ¥ / 人民币）
     */
    private void collectCnyAmounts(String text, List<RiskDetectResultVO.AmountMatch> matches) {
        Matcher matcher = CNY_PATTERN.matcher(text);
        while (matcher.find()) {
            String raw = matcher.group(0);
            double amount = parseAmount(matcher.group(1));
            if (amount > 0) {
                matches.add(new RiskDetectResultVO.AmountMatch(raw, amount, "CNY"));
            }
        }
    }

    /**
     * 解析美元金额（$ / USD，按 7.2 汇率折算）
     */
    private void collectUsdAmounts(String text, List<RiskDetectResultVO.AmountMatch> matches) {
        Matcher matcher = USD_PATTERN.matcher(text);
        while (matcher.find()) {
            String raw = matcher.group(0);
            double amount = parseAmount(matcher.group(1)) * 7.2;
            if (amount > 0) {
                matches.add(new RiskDetectResultVO.AmountMatch(raw, amount, "USD"));
            }
        }
    }

    /**
     * 解析中文金额带单位（万元 / 万 / 元）
     */
    private void collectCnUnitAmounts(String text, List<RiskDetectResultVO.AmountMatch> matches) {
        Matcher matcher = CN_UNIT_PATTERN.matcher(text);
        while (matcher.find()) {
            String raw = matcher.group(0);
            String unit = matcher.group(2);
            double amount = parseAmount(matcher.group(1));
            if ("万元".equals(unit)) {
                amount *= 10_000;
            } else if ("万".equals(unit)) {
                amount *= 10_000;
            }
            // "元"单位不换算
            if (amount > 0) {
                matches.add(new RiskDetectResultVO.AmountMatch(raw, amount, "CNY"));
            }
        }
    }

    /**
     * 解析金额前缀关键词（金额：50000 / amount: 10000）
     */
    private void collectAmountKeywordAmounts(String text, List<RiskDetectResultVO.AmountMatch> matches) {
        Matcher matcher = AMOUNT_KEYWORD_PATTERN.matcher(text);
        while (matcher.find()) {
            String raw = matcher.group(0);
            double amount = parseAmount(matcher.group(1));
            if (amount > 0) {
                matches.add(new RiskDetectResultVO.AmountMatch(raw, amount, "CNY"));
            }
        }
    }

    /**
     * 解析金额字符串（去除逗号）
     *
     * @param amountStr 金额字符串（如 "50,000.00"）
     * @return 金额数值，解析失败返回 0
     */
    private double parseAmount(String amountStr) {
        if (amountStr == null || amountStr.isEmpty()) {
            return 0;
        }
        try {
            return Double.parseDouble(amountStr.replace(",", "").trim());
        } catch (NumberFormatException e) {
            log.warn("解析金额失败: {}", amountStr);
            return 0;
        }
    }

    // endregion

    // region 私有方法：风险等级判定

    /**
     * 构建预筛结果（含风险等级判定）
     *
     * @param request       预筛请求
     * @param hitKeywords   命中关键词列表
     * @param amountMatches 金额匹配列表
     * @return 预筛结果 VO
     */
    private RiskDetectResultVO buildResult(RiskDetectRequest request,
                                            List<RiskDetectResultVO.HitKeyword> hitKeywords,
                                            List<RiskDetectResultVO.AmountMatch> amountMatches) {
        RiskDetectResultVO result = new RiskDetectResultVO();
        result.setTaskId(request.getTaskId());
        result.setIndustry(request.getIndustry());
        result.setHitKeywords(hitKeywords);
        result.setAmountMatches(amountMatches);

        // 1. 统计命中数
        int highRiskHitCount = 0;
        int sensitiveDataHitCount = 0;
        int mediumRiskHitCount = 0;
        for (RiskDetectResultVO.HitKeyword hit : hitKeywords) {
            if (ApprovalConstant.RISK_TYPE_HIGH.equals(hit.getRiskType())) {
                if (ApprovalConstant.CATEGORY_HIGH_RISK_OPERATION.equals(hit.getCategory())) {
                    highRiskHitCount++;
                } else if (ApprovalConstant.CATEGORY_SENSITIVE_DATA.equals(hit.getCategory())) {
                    sensitiveDataHitCount++;
                } else {
                    highRiskHitCount++;
                }
            } else if (ApprovalConstant.RISK_TYPE_MEDIUM.equals(hit.getRiskType())) {
                mediumRiskHitCount++;
            }
        }
        result.setHighRiskHitCount(highRiskHitCount + sensitiveDataHitCount);
        result.setMediumRiskHitCount(mediumRiskHitCount);

        // 2. 计算最大金额
        double maxAmount = amountMatches.stream()
                .mapToDouble(RiskDetectResultVO.AmountMatch::getAmount)
                .max()
                .orElse(0.0);
        result.setMaxAmount(maxAmount);

        // 3. 判定是否命中大额阈值
        double threshold = getLargeAmountThreshold(request.getIndustry());
        boolean largeAmountHit = maxAmount >= threshold;
        result.setLargeAmountHit(largeAmountHit);

        // 4. 风险等级判定
        String riskLevel = determineRiskLevel(highRiskHitCount, sensitiveDataHitCount,
                mediumRiskHitCount, largeAmountHit);
        result.setSuggestedRiskLevel(riskLevel);

        // 5. 建议动作
        String action = isHighOrCritical(riskLevel) || (mediumRiskHitCount > 0 && largeAmountHit)
                ? ApprovalConstant.ACTION_JUDGE : ApprovalConstant.ACTION_PROCEED;
        result.setSuggestedAction(action);

        return result;
    }

    /**
     * 风险等级判定
     *
     * <p>规则：
     * <ul>
     *   <li>critical —— 同时命中高风险操作 + 敏感数据</li>
     *   <li>high —— 命中高风险操作 或 敏感数据 或 大额</li>
     *   <li>medium —— 命中中风险关键词</li>
     *   <li>low —— 无命中</li>
     * </ul>
     *
     * @param highRiskHitCount    高风险操作命中数
     * @param sensitiveDataHitCount 敏感数据命中数
     * @param mediumRiskHitCount  中风险命中数
     * @param largeAmountHit      是否命中大额阈值
     * @return 风险等级
     */
    private String determineRiskLevel(int highRiskHitCount, int sensitiveDataHitCount,
                                       int mediumRiskHitCount, boolean largeAmountHit) {
        // critical：同时命中高风险操作 + 敏感数据
        if (highRiskHitCount > 0 && sensitiveDataHitCount > 0) {
            return "critical";
        }
        // high：命中高风险操作 或 敏感数据 或 大额
        if (highRiskHitCount > 0 || sensitiveDataHitCount > 0 || largeAmountHit) {
            return "high";
        }
        // medium：命中中风险关键词
        if (mediumRiskHitCount > 0) {
            return "medium";
        }
        // low：无命中
        return "low";
    }

    /**
     * 判断风险等级是否为 high 或 critical
     *
     * @param riskLevel 风险等级
     * @return true-是 high / critical
     */
    private boolean isHighOrCritical(String riskLevel) {
        return "high".equals(riskLevel) || "critical".equals(riskLevel);
    }

    /**
     * 获取行业对应的大额阈值
     *
     * @param industry 行业
     * @return 大额阈值（元）
     */
    private double getLargeAmountThreshold(String industry) {
        if (industry == null) {
            return ApprovalConstant.DEFAULT_LARGE_AMOUNT_THRESHOLD;
        }
        return switch (industry) {
            case "banking" -> ApprovalConstant.BANKING_LARGE_AMOUNT_THRESHOLD;
            case "insurance" -> ApprovalConstant.INSURANCE_LARGE_AMOUNT_THRESHOLD;
            case "securities" -> ApprovalConstant.SECURITIES_LARGE_AMOUNT_THRESHOLD;
            default -> ApprovalConstant.DEFAULT_LARGE_AMOUNT_THRESHOLD;
        };
    }

    // endregion

    // region 私有方法：构建 LLM 判断请求

    /**
     * 构建调 Python 的 LLM 判断请求
     *
     * @param request      原预筛请求
     * @param detectResult 预筛结果
     * @return LLM 判断请求
     */
    private RiskJudgeRequest buildJudgeRequest(RiskDetectRequest request, RiskDetectResultVO detectResult) {
        RiskJudgeRequest judgeRequest = new RiskJudgeRequest();
        judgeRequest.setTaskId(request.getTaskId() != null ? String.valueOf(request.getTaskId()) : null);
        judgeRequest.setGoal(request.getGoal());
        judgeRequest.setParams(request.getParams());
        judgeRequest.setIndustry(request.getIndustry());
        judgeRequest.setPreScreenRiskLevel(detectResult.getSuggestedRiskLevel());
        judgeRequest.setMaxAmount(detectResult.getMaxAmount());

        // 命中关键词转为 Map 列表
        List<Map<String, Object>> hitKeywordMaps = new ArrayList<>();
        for (RiskDetectResultVO.HitKeyword hit : detectResult.getHitKeywords()) {
            Map<String, Object> map = new HashMap<>();
            map.put("keyword", hit.getKeyword());
            map.put("industry", hit.getIndustry());
            map.put("category", hit.getCategory());
            map.put("riskType", hit.getRiskType());
            map.put("description", hit.getDescription());
            hitKeywordMaps.add(map);
        }
        judgeRequest.setHitKeywords(hitKeywordMaps);

        // 金额匹配转为 Map 列表
        List<Map<String, Object>> amountMaps = new ArrayList<>();
        for (RiskDetectResultVO.AmountMatch amount : detectResult.getAmountMatches()) {
            Map<String, Object> map = new HashMap<>();
            map.put("rawText", amount.getRawText());
            map.put("amount", amount.getAmount());
            map.put("currency", amount.getCurrency());
            amountMaps.add(map);
        }
        judgeRequest.setAmountMatches(amountMaps);

        return judgeRequest;
    }

    // endregion
}
