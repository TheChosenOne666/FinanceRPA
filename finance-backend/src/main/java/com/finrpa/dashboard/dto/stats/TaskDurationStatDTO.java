package com.finrpa.dashboard.dto.stats;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 任务执行时长统计 DTO（用于概览性能指标）
 *
 * <p>基于已终态任务（SUCCESS/FAILED/ABORTED）的 update_time - create_time 计算。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
public class TaskDurationStatDTO implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 平均执行时长（毫秒） */
    private Double avgDurationMs;

    /** P95 执行时长（毫秒） */
    private Double p95DurationMs;
}
