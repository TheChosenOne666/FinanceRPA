package com.finrpa.approval.dto.response;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 风险预筛结果 VO
 *
 * <p>包含关键词命中列表、金额匹配列表、判定的风险等级与建议动作。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
public class RiskDetectResultVO implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 任务 ID（可空，用于日志追踪） */
    private Long taskId;

    /** 所属行业 */
    private String industry;

    /** 命中的关键词列表 */
    private List<HitKeyword> hitKeywords = new ArrayList<>();

    /** 命中的金额列表 */
    private List<AmountMatch> amountMatches = new ArrayList<>();

    /** 高风险命中数（high_risk_operation + sensitive_data） */
    private int highRiskHitCount = 0;

    /** 中风险命中数（large_amount） */
    private int mediumRiskHitCount = 0;

    /** 最大金额（元，未命中为 0） */
    private double maxAmount = 0.0;

    /** 是否命中大额阈值 */
    private boolean largeAmountHit = false;

    /** 预筛判定的风险等级：low / medium / high / critical */
    private String suggestedRiskLevel = "low";

    /** 建议动作：proceed（直接执行）/ judge（调 LLM 二次判断） */
    private String suggestedAction = "proceed";

    /** 预筛耗时（毫秒） */
    private long durationMs = 0L;

    /**
     * 命中关键词信息
     */
    @Data
    public static class HitKeyword implements Serializable {
        /** 序列化版本号 */
        @Serial
        private static final long serialVersionUID = 1L;

        /** 关键词文本 */
        private String keyword;
        /** 所属行业 */
        private String industry;
        /** 分类 */
        private String category;
        /** 风险类型 */
        private String riskType;
        /** 描述说明 */
        private String description;
        /** 命中位置（在文本中的起始索引） */
        private int position = -1;

        public HitKeyword() {
        }

        public HitKeyword(String keyword, String industry, String category, String riskType, String description) {
            this.keyword = keyword;
            this.industry = industry;
            this.category = category;
            this.riskType = riskType;
            this.description = description;
        }
    }

    /**
     * 金额匹配信息
     */
    @Data
    public static class AmountMatch implements Serializable {
        /** 序列化版本号 */
        @Serial
        private static final long serialVersionUID = 1L;

        /** 匹配到的原始文本（如"￥50,000.00"） */
        private String rawText;
        /** 解析后的金额（元） */
        private double amount;
        /** 币种（CNY / USD） */
        private String currency;

        public AmountMatch() {
        }

        public AmountMatch(String rawText, double amount, String currency) {
            this.rawText = rawText;
            this.amount = amount;
            this.currency = currency;
        }
    }
}
