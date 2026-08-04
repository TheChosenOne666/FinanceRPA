package com.finrpa.auth.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 用户分配角色请求 DTO（P1 USR-1，三维度 RBAC）
 *
 * <p>每条关联由 {@code roleId + departmentId + businessLineId} 三元组组成；
 * {@code departmentId / businessLineId} 允许为 {@code null}，表示不限部门 / 业务线。
 * 该接口为<b>全量替换</b>语义：调用后用户的所有角色关联被替换为 {@code relations} 列表。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
public class UserRoleAssignRequest implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 用户业务 ID（必填） */
    @NotNull(message = "用户 ID 不能为空")
    private Long userId;

    /** 角色关联列表（全量替换） */
    @NotNull(message = "角色关联列表不能为空")
    private List<UserRoleRelation> relations;

    /**
     * 角色关联三元组
     */
    @Data
    public static class UserRoleRelation implements Serializable {

        /** 序列化版本号 */
        @Serial
        private static final long serialVersionUID = 1L;

        /** 角色 ID（必填） */
        @NotNull(message = "角色 ID 不能为空")
        private Long roleId;

        /** 部门业务 ID（null 表示不限部门） */
        private Long departmentId;

        /** 业务线业务 ID（null 表示不限业务线） */
        private Long businessLineId;
    }
}
