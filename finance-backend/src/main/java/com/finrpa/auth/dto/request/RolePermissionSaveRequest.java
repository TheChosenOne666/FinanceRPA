package com.finrpa.auth.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 角色权限保存请求（全量替换语义）
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
public class RolePermissionSaveRequest implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 权限 ID 集合（全量替换：传空列表表示清空该角色全部权限） */
    @NotEmpty(message = "权限 ID 集合不能为空（如需清空请传空数组并显式声明）")
    private List<Long> permissionIds;
}
