package com.finrpa.approval.enums;

import com.finrpa.approval.constant.ApprovalConstant;

/**
 * 审批状态枚举（M6.3）
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
public enum ApprovalStatusEnum {

    /** 待审批 */
    PENDING(ApprovalConstant.APPROVAL_STATUS_PENDING),

    /** 已通过 */
    APPROVED(ApprovalConstant.APPROVAL_STATUS_APPROVED),

    /** 已拒绝 */
    REJECTED(ApprovalConstant.APPROVAL_STATUS_REJECTED),

    /** 已超时 */
    TIMEOUT(ApprovalConstant.APPROVAL_STATUS_TIMEOUT);

    /** 状态值 */
    private final String value;

    ApprovalStatusEnum(String value) {
        this.value = value;
    }

    /**
     * 获取状态值
     *
     * @return 状态值
     */
    public String getValue() {
        return value;
    }

    /**
     * 根据 value 获取枚举
     *
     * @param value 状态值
     * @return 枚举实例
     */
    public static ApprovalStatusEnum fromValue(String value) {
        for (ApprovalStatusEnum status : values()) {
            if (status.value.equals(value)) {
                return status;
            }
        }
        return null;
    }

    /**
     * 判断是否为终态（已通过 / 已拒绝 / 已超时）
     *
     * @param value 状态值
     * @return true-是终态
     */
    public static boolean isTerminal(String value) {
        ApprovalStatusEnum status = fromValue(value);
        return status == APPROVED || status == REJECTED || status == TIMEOUT;
    }
}
