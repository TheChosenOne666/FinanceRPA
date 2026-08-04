package com.finrpa.auth.dto.request;

import com.finrpa.common.model.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 用户分页查询请求 DTO（P1 USR-1）
 *
 * <p>查询条件：用户名 / 真实姓名 模糊搜索 + 状态 + 组织 ID（org_admin 仅能查本组织）。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UserQueryRequest extends PageRequest {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 关键词（用户名 / 真实姓名 模糊匹配） */
    private String keyword;

    /** 状态（0-禁用 1-启用），null 表示全部 */
    private Integer status;

    /** 组织业务 ID（super_admin 可指定任意组织查询，org_admin 自动限定为当前组织） */
    private Long orgId;
}
