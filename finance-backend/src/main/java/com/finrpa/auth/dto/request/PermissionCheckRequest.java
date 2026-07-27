package com.finrpa.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 权限检查请求 DTO
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
public class PermissionCheckRequest {

    /** 资源类型 */
    @NotBlank(message = "资源类型不能为空")
    private String resourceType;

    /** 资源 ID */
    @NotBlank(message = "资源ID不能为空")
    private String resourceId;

    /** 操作类型 */
    @NotBlank(message = "操作类型不能为空")
    private String action;
}