package com.finrpa.approval.dto.response;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 风险二次判断响应 DTO（Python → Java，M6.2 实现 Python 端）
 *
 * <p>Python LLM 风险判断结果，输出 final_risk_level：low / medium / high / critical。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
public class RiskJudgeResponse implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 任务 ID */
    private String taskId;

    /** 最终风险等级：low / medium / high / critical */
    private String finalRiskLevel;

    /** LLM 判断理由 */
    private String reasoning;

    /** 建议审批路由：auto（自动通过）/ department（部门审批）/ compliance（合规审计部审批） */
    private String approvalRoute;

    /** 响应消息 */
    private String message;
}
