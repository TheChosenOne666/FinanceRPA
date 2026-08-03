package com.finrpa.dashboard.dto.response;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 业务线统计 VO（对齐系统设计 6.9.1 业务：各业务线任务分布 + 成功率对比）
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
public class BusinessLineStatVO implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 业务线 ID */
    private Long businessLineId;

    /** 业务线名称 */
    private String businessLineName;

    /** 任务总数（按 distinct task_id 计数） */
    private Long taskCount;

    /** 成功任务数 */
    private Long successCount;

    /** 成功率（0-1） */
    private Double successRate;
}
