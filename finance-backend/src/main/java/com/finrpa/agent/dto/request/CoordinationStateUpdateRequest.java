package com.finrpa.agent.dto.request;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 协调状态更新请求 DTO（Python 回调）
 *
 * <p>Python Coordinator 每步执行后回调此接口持久化 {@code CoordinationState}，
 * 用于断点续跑和 replan 追踪。Java 侧 upsert 到 {@code rpa_agent_coordination_state} 表。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
public class CoordinationStateUpdateRequest implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 导航目标 */
    private String navigationGoal;

    /** 当前计划（JSON 字符串，含子任务列表） */
    private String currentPlan;

    /** 已完成子任务 ID 列表 */
    private List<String> completedSubtasks;

    /** 总重规划次数 */
    private Integer totalReplans;

    /** 最大重规划次数 */
    private Integer maxReplans;

    /** 协调状态：RUNNING / COMPLETED / FAILED / NEEDS_HUMAN */
    private String status;

    /** 错误信息 */
    private String errorMessage;
}
