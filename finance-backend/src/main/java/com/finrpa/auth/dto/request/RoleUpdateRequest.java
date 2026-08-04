package com.finrpa.auth.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 编辑角色请求 DTO（P1 USR-2）
 *
 * <p>仅允许修改角色名称、描述、跨组织读/审批、状态；角色编码不可改（内置角色仅可改状态/描述）。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
public class RoleUpdateRequest implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 角色业务 ID（必填） */
    @NotNull(message = "角色 ID 不能为空")
    private Long roleId;

    /** 角色名称 */
    @Size(max = 32, message = "角色名称长度不能超过 32")
    private String roleName;

    /** 角色描述 */
    @Size(max = 256, message = "角色描述长度不能超过 256")
    private String description;

    /** 是否允许跨组织读取（0-否 1-是） */
    private Integer isCrossOrgRead;

    /** 是否允许跨组织审批（0-否 1-是） */
    private Integer isCrossOrgApprove;

    /** 状态（0-禁用 1-启用） */
    private Integer status;
}
