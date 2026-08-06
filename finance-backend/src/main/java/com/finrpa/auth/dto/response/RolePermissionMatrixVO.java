package com.finrpa.auth.dto.response;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 角色权限矩阵行视图对象
 *
 * <p>每行代表一个角色，含其已勾选的权限 ID 集合（用于前端矩阵勾选展示）。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
public class RolePermissionMatrixVO implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 角色业务 ID */
    private Long roleId;

    /** 角色编码 */
    private String roleCode;

    /** 角色名称 */
    private String roleName;

    /** 是否内置角色（内置角色权限可查看，建议不强制修改） */
    private Boolean builtIn;

    /** 已勾选的权限 ID 集合 */
    private List<Long> permissionIds;
}
