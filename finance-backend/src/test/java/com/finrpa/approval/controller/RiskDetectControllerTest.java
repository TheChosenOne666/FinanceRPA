package com.finrpa.approval.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finrpa.approval.dto.request.RiskDetectRequest;
import com.finrpa.approval.dto.response.RiskDetectResultVO;
import com.finrpa.approval.dto.response.RiskJudgeResponse;
import com.finrpa.approval.service.RiskDetectService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 风险检测控制器单元测试
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
class RiskDetectControllerTest {

    /** JSON 序列化工具 */
    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockMvc mockMvc;
    private RiskDetectService riskDetectService;

    @BeforeEach
    void setUp() {
        riskDetectService = mock(RiskDetectService.class);

        RiskDetectController controller = new RiskDetectController();
        org.springframework.test.util.ReflectionTestUtils.setField(controller, "riskDetectService", riskDetectService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("detect - 关键词预筛接口返回结果")
    void detect_Success() throws Exception {
        RiskDetectRequest request = new RiskDetectRequest();
        request.setGoal("执行转账操作");
        request.setIndustry("banking");

        RiskDetectResultVO result = new RiskDetectResultVO();
        result.setSuggestedRiskLevel("high");
        result.setSuggestedAction("judge");
        result.setHighRiskHitCount(1);
        when(riskDetectService.detect(any(RiskDetectRequest.class))).thenReturn(result);

        mockMvc.perform(post("/risk/detect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.suggestedRiskLevel").value("high"))
                .andExpect(jsonPath("$.data.suggestedAction").value("judge"));
    }

    @Test
    @DisplayName("detect - params 参数传递正确")
    void detect_WithParams_Success() throws Exception {
        RiskDetectRequest request = new RiskDetectRequest();
        request.setGoal("执行任务");
        Map<String, Object> params = new HashMap<>();
        params.put("amount", "50000");
        request.setParams(params);
        request.setIndustry("banking");

        RiskDetectResultVO result = new RiskDetectResultVO();
        result.setSuggestedRiskLevel("low");
        result.setSuggestedAction("proceed");
        when(riskDetectService.detect(any(RiskDetectRequest.class))).thenReturn(result);

        mockMvc.perform(post("/risk/detect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.suggestedRiskLevel").value("low"));
    }

    @Test
    @DisplayName("detectAndJudge - 低风险时返回 null")
    void detectAndJudge_LowRisk_ReturnsNull() throws Exception {
        RiskDetectRequest request = new RiskDetectRequest();
        request.setGoal("下载报表");
        request.setIndustry("banking");

        when(riskDetectService.detectAndJudge(any(RiskDetectRequest.class))).thenReturn(null);

        mockMvc.perform(post("/risk/detect-and-judge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("detectAndJudge - 高风险时返回 LLM 判断结果")
    void detectAndJudge_HighRisk_ReturnsJudgeResponse() throws Exception {
        RiskDetectRequest request = new RiskDetectRequest();
        request.setGoal("执行转账");
        request.setIndustry("banking");

        RiskJudgeResponse judgeResponse = new RiskJudgeResponse();
        judgeResponse.setFinalRiskLevel("high");
        judgeResponse.setApprovalRoute("department");
        judgeResponse.setReasoning("转账操作涉及资金流动");
        when(riskDetectService.detectAndJudge(any(RiskDetectRequest.class))).thenReturn(judgeResponse);

        mockMvc.perform(post("/risk/detect-and-judge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.finalRiskLevel").value("high"))
                .andExpect(jsonPath("$.data.approvalRoute").value("department"));
    }
}
