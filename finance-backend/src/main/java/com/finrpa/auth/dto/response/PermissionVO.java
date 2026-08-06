package com.finrpa.auth.dto.response;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 权限点视图对象
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
public class PermissionVO implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 权限业务 ID */
    private Long permissionId;

    /** 权限编码（如 task:create） */
    private String permissionCode;

    /** 权限名称（如 任务创建） */
    private String permissionName;

    /** 资源类型（task / workflow / user / role / org / report / all） */
    private String resourceType;

    /** 资源路径（可空） */
    private String resourcePath;

    /** 排序序号 */
    private Integer sortOrder;
}
