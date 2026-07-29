package com.finrpa.tenant.dto.response;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 部门视图对象（GET /tenant/departments 列表项）
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
public class DepartmentVO implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 部门业务 ID（雪花算法 ID） */
    private Long deptId;

    /** 部门名称 */
    private String deptName;

    /** 部门编码 */
    private String deptCode;

    /** 父部门 ID（0-顶级部门） */
    private Long parentId;

    /** 排序序号 */
    private Integer sortOrder;

    /** 状态（0-禁用 1-启用） */
    private Integer status;
}
