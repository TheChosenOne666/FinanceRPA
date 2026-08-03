package com.finrpa.dashboard.dto.stats;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 任务量趋势单日聚合 DTO
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
public class TrendTaskDTO implements Serializable {

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
}
