package com.finrpa.agent.event;

import org.springframework.context.ApplicationEvent;

/**
 * 任务进入终态事件（M8.1 引入）
 *
 * <p>当任务状态流转至终态（SUCCESS / FAILED / ABORTED）时由 {@code TaskServiceImpl} 发布，
 * 供大屏缓存监听器主动失效该组织的统计缓存（对齐系统设计 6.9.2 缓存失效策略）。
 * 亦可被通知、告警等其他模块监听。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
public class TaskTerminalEvent extends ApplicationEvent {

    /** 任务 ID */
    private final Long taskId;

    /** 组织 ID（用于失效该租户的缓存） */
    private final Long orgId;

    /** 新状态（终态：SUCCESS / FAILED / ABORTED） */
    private final String newStatus;

    /** 前置状态 */
    private final String previousStatus;

    /**
     * 构造任务终态事件
     *
     * @param source         事件源（发布者）
     * @param taskId         任务 ID
     * @param orgId          组织 ID
     * @param newStatus      新状态（终态）
     * @param previousStatus 前置状态
     */
    public TaskTerminalEvent(Object source, Long taskId, Long orgId, String newStatus, String previousStatus) {
        super(source);
        this.taskId = taskId;
        this.orgId = orgId;
        this.newStatus = newStatus;
        this.previousStatus = previousStatus;
    }

    /**
     * 获取任务 ID
     *
     * @return 任务 ID
     */
    public Long getTaskId() {
        return taskId;
    }

    /**
     * 获取组织 ID
     *
     * @return 组织 ID
     */
    public Long getOrgId() {
        return orgId;
    }

    /**
     * 获取新状态
     *
     * @return 新状态
     */
    public String getNewStatus() {
        return newStatus;
    }

    /**
     * 获取前置状态
     *
     * @return 前置状态
     */
    public String getPreviousStatus() {
        return previousStatus;
    }
}
