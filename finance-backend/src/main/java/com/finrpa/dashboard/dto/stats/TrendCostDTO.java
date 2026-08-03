package com.finrpa.dashboard.dto.stats;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * LLM 成本趋势单日聚合 DTO
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
public class TrendCostDTO implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 日期（yyyy-MM-dd） */
    private String date;

    /** 当日 LLM 成本（美元） */
    private BigDecimal cost;
}
