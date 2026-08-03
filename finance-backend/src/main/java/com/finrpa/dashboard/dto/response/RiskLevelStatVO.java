package com.finrpa.dashboard.dto.response;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 风险等级统计 VO（用于概览页风险等级分布）
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
public class RiskLevelStatVO implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 风险等级：low / medium / high / critical */
    private String riskLevel;

    /** 该风险等级的任务数 */
    private Long count;

    /**
     * 默认构造（供 MyBatis 映射使用）
     */
    public RiskLevelStatVO() {
    }

    /**
     * 全参构造
     *
     * @param riskLevel 风险等级
     * @param count     任务数
     */
    public RiskLevelStatVO(String riskLevel, Long count) {
        this.riskLevel = riskLevel;
        this.count = count;
    }
}
