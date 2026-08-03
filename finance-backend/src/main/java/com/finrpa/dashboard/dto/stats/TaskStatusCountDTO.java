package com.finrpa.dashboard.dto.stats;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 任务状态分组计数 DTO（用于概览任务指标聚合）
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
public class TaskStatusCountDTO implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 任务状态 */
    private String status;

    /** 该状态任务数 */
    private Long count;
}
