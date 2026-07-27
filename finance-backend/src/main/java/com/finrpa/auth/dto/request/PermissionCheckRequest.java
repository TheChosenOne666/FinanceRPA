package com.finrpa.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PermissionCheckRequest {

    @NotBlank(message = "资源类型不能为空")
    private String resourceType;

    @NotBlank(message = "资源ID不能为空")
    private String resourceId;

    @NotBlank(message = "操作类型不能为空")
    private String action;
}