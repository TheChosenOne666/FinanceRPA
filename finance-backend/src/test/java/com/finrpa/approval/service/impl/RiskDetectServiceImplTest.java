package com.finrpa.approval.service.impl;

import com.finrpa.ai.client.AiServiceClient;
import com.finrpa.approval.constant.ApprovalConstant;
import com.finrpa.approval.dto.request.RiskDetectRequest;
import com.finrpa.approval.dto.request.RiskJudgeRequest;
import com.finrpa.approval.dto.response.RiskDetectResultVO;
import com.finrpa.approval.dto.response.RiskJudgeResponse;
import com.finrpa.approval.entity.RiskKeywordEO;
import com.finrpa.approval.service.RiskKeywordService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 风险检测服务实现单元测试
 *
 * <p>覆盖：关键词匹配、金额正则检测、风险等级判定、detectAndJudge 流程。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@ExtendWith(MockitoExtension.class)
class RiskDetectServiceImplTest {

    /** 测试用任务 ID */
    private static final Long TEST_TASK_ID = 2082333099000000099L;

    @Mock
    private RiskKeywordService riskKeywordService;

    @Mock
    private AiServiceClient aiServiceClient;

    @InjectMocks
    private RiskDetectServiceImpl riskDetectService;

    // region detect 关键词匹配

    @Test
    @DisplayName("detect - 无命中关键词返回 low 风险")
    void detect_NoHit_ReturnsLow() {
        RiskDetectRequest request = buildRequest("下载银行流水", "banking");
        when(riskKeywordService.listEnabledKeywords("banking"))
                .thenReturn(buildKeywords(
                        kw("转账", "banking", "high_risk_operation", "high"),
                        kw("银行卡号", "banking", "sensitive_data", "high")
                ));

        RiskDetectResultVO result = riskDetectService.detect(request);

        assertEquals("low", result.getSuggestedRiskLevel());
        assertEquals("proceed", result.getSuggestedAction());
        assertTrue(result.getHitKeywords().isEmpty());
        assertEquals(0, result.getHighRiskHitCount());
        assertEquals(0, result.getMediumRiskHitCount());
        assertFalse(result.isLargeAmountHit());
    }

    @Test
    @DisplayName("detect - 命中高风险操作关键词返回 high 风险")
    void detect_HitHighRiskOperation_ReturnsHigh() {
        RiskDetectRequest request = buildRequest("执行转账操作", "banking");
        when(riskKeywordService.listEnabledKeywords("banking"))
                .thenReturn(buildKeywords(
                        kw("转账", "banking", "high_risk_operation", "high"),
                        kw("银行卡号", "banking", "sensitive_data", "high")
                ));

        RiskDetectResultVO result = riskDetectService.detect(request);

        assertEquals("high", result.getSuggestedRiskLevel());
        assertEquals("judge", result.getSuggestedAction());
        assertEquals(1, result.getHitKeywords().size());
        assertEquals("转账", result.getHitKeywords().get(0).getKeyword());
        assertEquals(1, result.getHighRiskHitCount());
    }

    @Test
    @DisplayName("detect - 同时命中高风险操作+敏感数据返回 critical")
    void detect_HitBothHighRiskAndSensitive_ReturnsCritical() {
        RiskDetectRequest request = buildRequest("执行转账操作，请输入银行卡号和密码", "banking");
        when(riskKeywordService.listEnabledKeywords("banking"))
                .thenReturn(buildKeywords(
                        kw("转账", "banking", "high_risk_operation", "high"),
                        kw("银行卡号", "banking", "sensitive_data", "high"),
                        kw("密码", "banking", "sensitive_data", "high")
                ));

        RiskDetectResultVO result = riskDetectService.detect(request);

        assertEquals("critical", result.getSuggestedRiskLevel());
        assertEquals("judge", result.getSuggestedAction());
        assertEquals(3, result.getHitKeywords().size());
        assertEquals(3, result.getHighRiskHitCount());
    }

    @Test
    @DisplayName("detect - 命中中风险关键词返回 medium 风险")
    void detect_HitMediumRisk_ReturnsMedium() {
        RiskDetectRequest request = buildRequest("查看保单保额信息", "insurance");
        when(riskKeywordService.listEnabledKeywords("insurance"))
                .thenReturn(buildKeywords(
                        kw("保额", "insurance", "large_amount", "medium")
                ));

        RiskDetectResultVO result = riskDetectService.detect(request);

        assertEquals("medium", result.getSuggestedRiskLevel());
        // medium 单独命中不触发 judge（需 medium + largeAmountHit）
        assertEquals("proceed", result.getSuggestedAction());
        assertEquals(1, result.getMediumRiskHitCount());
    }

    @Test
    @DisplayName("detect - params 中的值也参与关键词匹配")
    void detect_ParamsValue_ParticipatesInMatching() {
        RiskDetectRequest request = new RiskDetectRequest();
        request.setGoal("执行任务");
        Map<String, Object> params = new HashMap<>();
        params.put("action", "买入");
        params.put("account", "资金账号");
        request.setParams(params);
        request.setIndustry("securities");

        when(riskKeywordService.listEnabledKeywords("securities"))
                .thenReturn(buildKeywords(
                        kw("买入", "securities", "high_risk_operation", "high"),
                        kw("资金账号", "securities", "sensitive_data", "high")
                ));

        RiskDetectResultVO result = riskDetectService.detect(request);

        assertEquals("critical", result.getSuggestedRiskLevel());
        assertEquals(2, result.getHitKeywords().size());
    }

    @Test
    @DisplayName("detect - industry 为空时加载全部行业关键词")
    void detect_NullIndustry_LoadsAllIndustries() {
        RiskDetectRequest request = buildRequest("执行转账", null);
        when(riskKeywordService.listEnabledKeywords(null))
                .thenReturn(buildKeywords(
                        kw("转账", "banking", "high_risk_operation", "high"),
                        kw("退保", "insurance", "high_risk_operation", "high")
                ));

        RiskDetectResultVO result = riskDetectService.detect(request);

        assertEquals("high", result.getSuggestedRiskLevel());
        assertEquals(1, result.getHitKeywords().size());
        verify(riskKeywordService).listEnabledKeywords(null);
    }

    @Test
    @DisplayName("detect - 空关键词库返回 low")
    void detect_EmptyKeywordLibrary_ReturnsLow() {
        RiskDetectRequest request = buildRequest("任意操作", "banking");
        when(riskKeywordService.listEnabledKeywords("banking"))
                .thenReturn(Collections.emptyList());

        RiskDetectResultVO result = riskDetectService.detect(request);

        assertEquals("low", result.getSuggestedRiskLevel());
        assertTrue(result.getHitKeywords().isEmpty());
    }

    // endregion

    // region detect 金额检测

    @Test
    @DisplayName("detect - 人民币金额 ￥50000 命中银行业大额阈值")
    void detect_CnyAmount_HitsBankingThreshold() {
        RiskDetectRequest request = buildRequest("转账 ￥50,000.00 元", "banking");
        when(riskKeywordService.listEnabledKeywords("banking"))
                .thenReturn(buildKeywords(kw("转账", "banking", "high_risk_operation", "high")));

        RiskDetectResultVO result = riskDetectService.detect(request);

        // 转账命中 high + 大额命中 high → high（不是 critical，因无敏感数据）
        assertEquals("high", result.getSuggestedRiskLevel());
        assertTrue(result.isLargeAmountHit());
        assertEquals(50_000.0, result.getMaxAmount(), 0.001);
        assertFalse(result.getAmountMatches().isEmpty());
        assertEquals("CNY", result.getAmountMatches().get(0).getCurrency());
    }

    @Test
    @DisplayName("detect - 金额未达阈值不触发 largeAmountHit")
    void detect_AmountBelowThreshold_NoLargeAmountHit() {
        RiskDetectRequest request = buildRequest("查看余额 ￥1,000 元", "banking");
        when(riskKeywordService.listEnabledKeywords("banking"))
                .thenReturn(Collections.emptyList());

        RiskDetectResultVO result = riskDetectService.detect(request);

        assertEquals("low", result.getSuggestedRiskLevel());
        assertFalse(result.isLargeAmountHit());
        assertEquals(1000.0, result.getMaxAmount(), 0.001);
    }

    @Test
    @DisplayName("detect - 万元单位金额解析正确")
    void detect_WanYuanUnit_ParsedCorrectly() {
        RiskDetectRequest request = buildRequest("转账 100万元", "banking");
        when(riskKeywordService.listEnabledKeywords("banking"))
                .thenReturn(Collections.emptyList());

        RiskDetectResultVO result = riskDetectService.detect(request);

        assertEquals(1_000_000.0, result.getMaxAmount(), 0.001);
        assertTrue(result.isLargeAmountHit());
    }

    @Test
    @DisplayName("detect - 美元金额按 7.2 汇率折算")
    void detect_UsdAmount_ConvertedByRate() {
        RiskDetectRequest request = buildRequest("转账 $10,000", "banking");
        when(riskKeywordService.listEnabledKeywords("banking"))
                .thenReturn(Collections.emptyList());

        RiskDetectResultVO result = riskDetectService.detect(request);

        // $10,000 * 7.2 = 72000 元 > 50000 阈值
        assertEquals(72_000.0, result.getMaxAmount(), 0.001);
        assertTrue(result.isLargeAmountHit());
        assertEquals("USD", result.getAmountMatches().get(0).getCurrency());
    }

    @Test
    @DisplayName("detect - 证券行业大额阈值为 10 万元")
    void detect_SecuritiesThreshold_100k() {
        RiskDetectRequest request = buildRequest("买入 50,000元", "securities");
        when(riskKeywordService.listEnabledKeywords("securities"))
                .thenReturn(Collections.emptyList());

        RiskDetectResultVO result = riskDetectService.detect(request);

        // 5万 < 10万阈值，不触发大额
        assertFalse(result.isLargeAmountHit());
    }

    @Test
    @DisplayName("detect - 金额前缀关键词识别")
    void detect_AmountKeyword_Recognized() {
        RiskDetectRequest request = buildRequest("转账金额：20000", "banking");
        when(riskKeywordService.listEnabledKeywords("banking"))
                .thenReturn(Collections.emptyList());

        RiskDetectResultVO result = riskDetectService.detect(request);

        assertEquals(20_000.0, result.getMaxAmount(), 0.001);
    }

    @Test
    @DisplayName("detect - 多个金额取最大值")
    void detect_MultipleAmounts_TakesMax() {
        RiskDetectRequest request = buildRequest("小额 ￥1,000 大额 ￥80,000", "banking");
        when(riskKeywordService.listEnabledKeywords("banking"))
                .thenReturn(Collections.emptyList());

        RiskDetectResultVO result = riskDetectService.detect(request);

        assertEquals(80_000.0, result.getMaxAmount(), 0.001);
        assertTrue(result.isLargeAmountHit());
    }

    @Test
    @DisplayName("detect - 中风险关键词 + 大额命中触发 judge")
    void detect_MediumRiskPlusLargeAmount_TriggersJudge() {
        RiskDetectRequest request = buildRequest("保额 ￥20,000 元", "insurance");
        when(riskKeywordService.listEnabledKeywords("insurance"))
                .thenReturn(buildKeywords(kw("保额", "insurance", "large_amount", "medium")));

        RiskDetectResultVO result = riskDetectService.detect(request);

        // 保额命中 medium + 大额命中（保险阈值 1万）→ 大额命中升级为 high → judge
        assertEquals("high", result.getSuggestedRiskLevel());
        assertTrue(result.isLargeAmountHit());
        assertEquals(1, result.getMediumRiskHitCount());
        assertEquals("judge", result.getSuggestedAction());
    }

    // endregion

    // region detect 参数校验

    @Test
    @DisplayName("detect - 请求为空抛出参数异常")
    void detect_NullRequest_ThrowsException() {
        assertThrows(com.finrpa.common.exception.BusinessException.class,
                () -> riskDetectService.detect(null));
    }

    @Test
    @DisplayName("detect - goal 和 params 同时为空抛出异常")
    void detect_EmptyGoalAndParams_ThrowsException() {
        RiskDetectRequest request = new RiskDetectRequest();
        request.setIndustry("banking");

        assertThrows(com.finrpa.common.exception.BusinessException.class,
                () -> riskDetectService.detect(request));
    }

    // endregion

    // region detectAndJudge

    @Test
    @DisplayName("detectAndJudge - 低风险时不调 LLM 返回 null")
    void detectAndJudge_LowRisk_DoesNotCallLlm() {
        RiskDetectRequest request = buildRequest("下载报表", "banking");
        when(riskKeywordService.listEnabledKeywords("banking"))
                .thenReturn(Collections.emptyList());

        RiskJudgeResponse response = riskDetectService.detectAndJudge(request);

        assertNull(response);
        verify(aiServiceClient, never()).judgeRisk(any());
    }

    @Test
    @DisplayName("detectAndJudge - 高风险时调 LLM 返回判断结果")
    void detectAndJudge_HighRisk_CallsLlm() {
        RiskDetectRequest request = buildRequest("执行转账", "banking");
        when(riskKeywordService.listEnabledKeywords("banking"))
                .thenReturn(buildKeywords(kw("转账", "banking", "high_risk_operation", "high")));

        RiskJudgeResponse judgeResponse = new RiskJudgeResponse();
        judgeResponse.setFinalRiskLevel("high");
        judgeResponse.setReasoning("转账操作涉及资金流动");
        judgeResponse.setApprovalRoute("department");
        when(aiServiceClient.judgeRisk(any(RiskJudgeRequest.class))).thenReturn(judgeResponse);

        RiskJudgeResponse response = riskDetectService.detectAndJudge(request);

        assertNotNull(response);
        assertEquals("high", response.getFinalRiskLevel());
        assertEquals("department", response.getApprovalRoute());

        // 验证调用参数
        ArgumentCaptor<RiskJudgeRequest> captor = ArgumentCaptor.forClass(RiskJudgeRequest.class);
        verify(aiServiceClient).judgeRisk(captor.capture());
        RiskJudgeRequest judgeReq = captor.getValue();
        assertEquals("banking", judgeReq.getIndustry());
        assertEquals("high", judgeReq.getPreScreenRiskLevel());
        assertEquals(1, judgeReq.getHitKeywords().size());
    }

    @Test
    @DisplayName("detectAndJudge - LLM 调用失败时回退返回 null")
    void detectAndJudge_LlmFailure_ReturnsNull() {
        RiskDetectRequest request = buildRequest("执行转账", "banking");
        when(riskKeywordService.listEnabledKeywords("banking"))
                .thenReturn(buildKeywords(kw("转账", "banking", "high_risk_operation", "high")));
        when(aiServiceClient.judgeRisk(any(RiskJudgeRequest.class)))
                .thenThrow(new RuntimeException("Python service unavailable"));

        RiskJudgeResponse response = riskDetectService.detectAndJudge(request);

        assertNull(response);
        verify(aiServiceClient).judgeRisk(any(RiskJudgeRequest.class));
    }

    // endregion

    // region 测试辅助方法

    /**
     * 构建预筛请求
     */
    private RiskDetectRequest buildRequest(String goal, String industry) {
        RiskDetectRequest request = new RiskDetectRequest();
        request.setGoal(goal);
        request.setIndustry(industry);
        request.setTaskId(TEST_TASK_ID);
        return request;
    }

    /**
     * 构建关键词 EO
     */
    private RiskKeywordEO kw(String keyword, String industry, String category, String riskType) {
        RiskKeywordEO eo = new RiskKeywordEO();
        eo.setKeyword(keyword);
        eo.setIndustry(industry);
        eo.setCategory(category);
        eo.setRiskType(riskType);
        return eo;
    }

    /**
     * 构建关键词列表
     */
    private List<RiskKeywordEO> buildKeywords(RiskKeywordEO... keywords) {
        return Arrays.asList(keywords);
    }

    // endregion
}
