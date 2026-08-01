package com.finrpa.approval.controller;

import com.finrpa.approval.dto.request.RiskDetectRequest;
import com.finrpa.approval.dto.response.RiskDetectResultVO;
import com.finrpa.approval.dto.response.RiskJudgeResponse;
import com.finrpa.approval.service.RiskDetectService;
import com.finrpa.common.response.BaseResponse;
import com.finrpa.common.response.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 风险检测控制器（对外 API）
 *
 * <p>对外端点（实际访问路径前缀 {@code /api/risk}）：
 * <ul>
 *   <li>POST /risk/detect —— 关键词预筛（阶段 1，不调 LLM）</li>
 *   <li>POST /risk/detect-and-judge —— 预筛 + LLM 二次判断（阶段 1 + 阶段 2，M6.2 实现 Python 端）</li>
 * </ul>
 * </p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Slf4j
@RestController
@RequestMapping("/risk")
@Tag(name = "风险检测", description = "Java 关键词预筛 + LLM 风险二次判断")
public class RiskDetectController {

    /** 风险检测服务 */
    @Resource
    private RiskDetectService riskDetectService;

    /**
     * 关键词预筛（阶段 1）
     *
     * <p>对任务目标 + 参数进行关键词匹配与金额检测，返回预筛结果与建议风险等级。
     * 不调用 LLM，响应速度快（毫秒级）。</p>
     *
     * @param request 预筛请求
     * @return 预筛结果
     */
    @PostMapping("/detect")
    @Operation(summary = "关键词预筛", description = "对任务目标+参数进行关键词匹配与金额检测，不调 LLM")
    public BaseResponse<RiskDetectResultVO> detect(@RequestBody RiskDetectRequest request) {
        RiskDetectResultVO result = riskDetectService.detect(request);
        return ResultUtils.success(result);
    }

    /**
     * 预筛 + LLM 二次判断（阶段 1 + 阶段 2）
     *
     * <p>先执行关键词预筛，若建议动作为 judge（中高风险），则调 Python LLM 二次判断。
     * M6.1 阶段 Python 端尚未实现，调用失败时回退使用预筛结果。</p>
     *
     * @param request 预筛请求
     * @return LLM 判断响应（M6.1 阶段返回 null 表示未调用 LLM）
     */
    @PostMapping("/detect-and-judge")
    @Operation(summary = "预筛 + LLM 二次判断", description = "预筛命中中高风险时调 Python LLM 二次判断")
    public BaseResponse<RiskJudgeResponse> detectAndJudge(@RequestBody RiskDetectRequest request) {
        RiskJudgeResponse response = riskDetectService.detectAndJudge(request);
        return ResultUtils.success(response);
    }
}
