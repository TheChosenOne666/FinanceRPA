package com.finrpa.auth.dto.response;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.sql.Timestamp;

/**
 * 角色视图对象（P1 USR-2）
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
public class RoleVO implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 角色业务 ID（雪花算法 ID） */
    private Long roleId;

    /** 角色名称 */
    private String roleName;

    /** 角色编码 */
    private String roleCode;

    /** 角色描述 */
    private String description;

    /** 所属组织 ID */
    private Long orgId;

    /** 是否允许跨组织读取（0-否 1-是） */
    private Integer isCrossOrgRead;

    /** 是否允许跨组织审批（0-否 1-是） */
    private Integer isCrossOrgApprove;

    /** 状态（0-禁用 1-启用） */
    private Integer status;

    /** 是否为内置角色（基于 roleCode 判定，true 时禁止删除/修改编码） */
    private Boolean builtIn;

    /** 创建时间 */
    private Timestamp createTime;

    /** 更新时间 */
    private Timestamp updateTime;
}
