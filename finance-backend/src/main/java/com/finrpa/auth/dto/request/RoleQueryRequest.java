package com.finrpa.auth.dto.request;

import com.finrpa.common.model.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 角色分页查询请求 DTO（P1 USR-2）
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class RoleQueryRequest extends PageRequest {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 关键词（角色名称 / 编码 模糊匹配） */
    private String keyword;

    /** 状态（0-禁用 1-启用），null 表示全部 */
    private Integer status;

    /** 组织业务 ID（super_admin 可指定任意组织；org_admin 自动限定为当前组织） */
    private Long orgId;
}
