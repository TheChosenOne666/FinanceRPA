package com.finrpa.system.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 系统配置更新请求
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
public class SystemConfigUpdateRequest implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 配置值（必填） */
    @NotBlank(message = "配置值不能为空")
    private String configValue;

    /** 描述说明（可选，传 null 不更新） */
    private String description;

    /** 启用状态（可选，传 null 不更新；0-禁用 1-启用） */
    private Integer status;
}
