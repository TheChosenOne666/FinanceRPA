package com.finrpa.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 新增角色请求 DTO（P1 USR-2）
 *
 * <p>角色编码 + 角色名称必填；org_admin 新增的角色自动归属本组织（org_id = currentOrgId）。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
public class RoleAddRequest implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 角色名称 */
    @NotBlank(message = "角色名称不能为空")
    @Size(max = 32, message = "角色名称长度不能超过 32")
    private String roleName;

    /** 角色编码（唯一标识；内置编码 super_admin / org_admin / operator / approver / viewer 受保护，不允许新增） */
    @NotBlank(message = "角色编码不能为空")
    @Size(max = 64, message = "角色编码长度不能超过 64")
    @Pattern(regexp = "^[a-zA-Z][a-zA-Z0-9_]*$", message = "角色编码须以字母开头，仅允许字母、数字、下划线")
    private String roleCode;

    /** 角色描述 */
    @Size(max = 256, message = "角色描述长度不能超过 256")
    private String description;

    /** 是否允许跨组织读取（0-否 1-是），默认 0 */
    private Integer isCrossOrgRead;

    /** 是否允许跨组织审批（0-否 1-是），默认 0 */
    private Integer isCrossOrgApprove;

    /** 所属组织 ID（org_admin 新增时自动覆盖为当前组织；super_admin 可指定任意组织，null 表示全局角色） */
    private Long orgId;

    /** 状态（0-禁用 1-启用），默认 1 */
    private Integer status;
}
