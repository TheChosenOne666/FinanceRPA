package com.finrpa.dashboard.dto.response;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 运营大屏趋势 VO（对齐系统设计 6.9.1 任务量趋势 + 成本趋势）
 *
 * <p>按日聚合的任务量与 LLM 成本趋势，供前端 ECharts 折线图展示。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
public class TrendsVO implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 趋势数据点列表（按日期升序） */
    private List<TrendPointVO> points;

    /**
     * 趋势数据点（单日聚合）
     */
    @Data
    public static class TrendPointVO implements Serializable {

        /** 序列化版本号 */
        @Serial
        private static final long serialVersionUID = 1L;

        /** 日期（yyyy-MM-dd） */
        private String date;

        /** 当日任务总数 */
        private Long taskCount;

        /** 当日成功任务数 */
        private Long successCount;

        /** 当日失败任务数 */
        private Long failedCount;

        /** 当日 LLM 成本（美元） */
        private BigDecimal cost;
    }
}
