package com.finrpa.agent.service;

import com.finrpa.agent.enums.TaskStateEnum;
import com.finrpa.common.exception.BusinessException;
import com.finrpa.common.exception.ThrowUtils;
import com.finrpa.common.response.ErrorCode;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 任务状态机
 *
 * <p>定义任务状态的合法流转规则，非法流转抛出业务异常。</p>
 *
 * <p>合法流转：
 * <ul>
 *   <li>PENDING → EXECUTING</li>
 *   <li>EXECUTING → SUCCESS / FAILED / NEEDS_HUMAN / ABORTED</li>
 *   <li>NEEDS_HUMAN → EXECUTING（人工处置后恢复）/ ABORTED</li>
 * </ul>
 * </p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
public class TaskStateMachine {

    /** 状态流转规则表 */
    private static final Map<TaskStateEnum, Set<TaskStateEnum>> TRANSITIONS = new EnumMap<>(TaskStateEnum.class);

    static {
        // PENDING → EXECUTING
        TRANSITIONS.put(TaskStateEnum.PENDING, EnumSet.of(TaskStateEnum.EXECUTING, TaskStateEnum.ABORTED));
        // EXECUTING → SUCCESS / FAILED / NEEDS_HUMAN / ABORTED
        TRANSITIONS.put(TaskStateEnum.EXECUTING, EnumSet.of(
                TaskStateEnum.SUCCESS,
                TaskStateEnum.FAILED,
                TaskStateEnum.NEEDS_HUMAN,
                TaskStateEnum.ABORTED
        ));
        // NEEDS_HUMAN → EXECUTING（人工处置后恢复）/ ABORTED
        TRANSITIONS.put(TaskStateEnum.NEEDS_HUMAN, EnumSet.of(TaskStateEnum.EXECUTING, TaskStateEnum.ABORTED));
        // 终态（SUCCESS / FAILED / ABORTED）不允许流转
        TRANSITIONS.put(TaskStateEnum.SUCCESS, EnumSet.noneOf(TaskStateEnum.class));
        TRANSITIONS.put(TaskStateEnum.FAILED, EnumSet.noneOf(TaskStateEnum.class));
        TRANSITIONS.put(TaskStateEnum.ABORTED, EnumSet.noneOf(TaskStateEnum.class));
    }

    /**
     * 校验状态流转是否合法
     *
     * @param from 当前状态
     * @param to   目标状态
     */
    public static void validateTransition(TaskStateEnum from, TaskStateEnum to) {
        // 1. 校验参数非空
        ThrowUtils.throwIf(from == null || to == null, ErrorCode.PARAMS_ERROR, "状态不能为空");
        // 2. 校验流转合法性
        Set<TaskStateEnum> allowed = TRANSITIONS.get(from);
        ThrowUtils.throwIf(allowed == null || !allowed.contains(to),
                ErrorCode.OPERATION_ERROR, "非法状态流转: " + from.getValue() + " → " + to.getValue());
    }

    /**
     * 判断状态流转是否合法
     *
     * @param from 当前状态
     * @param to   目标状态
     * @return 是否合法
     */
    public static boolean canTransition(TaskStateEnum from, TaskStateEnum to) {
        if (from == null || to == null) {
            return false;
        }
        Set<TaskStateEnum> allowed = TRANSITIONS.get(from);
        return allowed != null && allowed.contains(to);
    }

    /**
     * 判断是否为终态
     *
     * @param state 任务状态
     * @return 是否为终态
     */
    public static boolean isTerminal(TaskStateEnum state) {
        return state == TaskStateEnum.SUCCESS
                || state == TaskStateEnum.FAILED
                || state == TaskStateEnum.ABORTED;
    }

    /**
     * 私有构造方法，禁止实例化
     */
    private TaskStateMachine() {
    }
}
