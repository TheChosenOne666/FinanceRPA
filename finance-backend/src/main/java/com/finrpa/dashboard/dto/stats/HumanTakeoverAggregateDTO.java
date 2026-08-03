package com.finrpa.dashboard.dto.stats;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 人工接管队列聚合统计 DTO（用于概览人工指标）
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
public class HumanTakeoverAggregateDTO implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 待处置队列长度（PENDING） */
    private Long queueSize;

    /** 平均处置时长（毫秒，基于已 RESOLVED 记录） */
    private Double avgResolveMs;
}
