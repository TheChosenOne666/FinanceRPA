package com.finrpa.approval.service;

import com.finrpa.approval.dto.request.RiskDetectRequest;
import com.finrpa.approval.dto.response.RiskDetectResultVO;
import com.finrpa.approval.dto.response.RiskJudgeResponse;

/**
 * 风险检测服务接口（阶段 1：关键词预筛）
 *
 * <p>M6.1 实现 Java 关键词预筛：
 * <ol>
 *   <li>加载关键词库（从 DB 读取，按行业过滤）</li>
 *   <li>对任务目标 + 参数文本进行关键词匹配</li>
 *   <li>金额正则检测（¥ / $ / 万元 / 元）</li>
 *   <li>风险等级判定（low / medium / high / critical）</li>
 *   <li>命中中高风险时调 Python LLM 二次判断（M6.2 实现，M6.1 预留接口）</li>
 * </ol>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
public interface RiskDetectService {

    /**
     * 关键词预筛（阶段 1，不调 LLM）
     *
     * <p>对任务目标 + 参数进行关键词匹配与金额检测，返回预筛结果与建议风险等级。</p>
     *
     * @param request 预筛请求
     * @return 预筛结果（含命中关键词、金额、建议风险等级、建议动作）
     */
    RiskDetectResultVO detect(RiskDetectRequest request);

    /**
     * 关键词预筛 + LLM 二次判断（阶段 1 + 阶段 2）
     *
     * <p>先执行预筛，若建议动作为 judge（中高风险），则调 Python LLM 二次判断（M6.2 实现）。
     * M6.1 阶段 Python 端尚未实现，调用失败时回退使用预筛结果。</p>
     *
     * @param request 预筛请求
     * @return LLM 判断响应（M6.1 阶段返回 null 表示未调用 LLM）
     */
    RiskJudgeResponse detectAndJudge(RiskDetectRequest request);
}
