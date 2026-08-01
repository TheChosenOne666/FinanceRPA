package com.finrpa.approval.dto.request;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 风险二次判断请求 DTO（Java → Python，M6.2 实现 Python 端）
 *
 * <p>M6.1 阶段在 {@code AiServiceClient} 中预留接口，实际 Python 端在 M6.2 实现。
 * 字段对齐 M6.2 任务描述：输入目标 + 参数 + 预筛结果 → 输出 final_risk_level。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
public class RiskJudgeRequest implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 任务 ID（字符串形式，与 Python 侧约定一致） */
    private String taskId;

    /** 组织 ID（字符串形式） */
    private String orgId;

    /** 任务目标 */
    private String goal;

    /** 任务参数 */
    private Map<String, Object> params;

    /** 所属行业：banking / insurance / securities */
    private String industry;

    /** 预筛判定的风险等级：low / medium / high / critical */
    private String preScreenRiskLevel;

    /** 预筛命中的关键词列表（每项含 keyword / industry / category / riskType / description） */
    private List<Map<String, Object>> hitKeywords;

    /** 预筛命中的金额列表（每项含 rawText / amount / currency） */
    private List<Map<String, Object>> amountMatches;

    /** 最大金额（元） */
    private Double maxAmount;
}
