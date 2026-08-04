package com.finrpa.auth.dto.response;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.sql.Timestamp;
import java.util.List;

/**
 * 用户视图对象（P1 USR-1，脱敏后返回前端）
 *
 * <p>不返回 password 字段；roles 列表由 sys_user_role 关联查询得到。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
public class UserVO implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 用户业务 ID（雪花算法 ID） */
    private Long userId;

    /** 用户名 */
    private String username;

    /** 真实姓名 */
    private String realName;

    /** 头像地址 */
    private String avatar;

    /** 邮箱 */
    private String email;

    /** 手机号 */
    private String phone;

    /** 所属组织 ID */
    private Long orgId;

    /** 所属组织名称 */
    private String orgName;

    /** 所属部门名称 */
    private String deptName;

    /** 状态（0-禁用 1-启用） */
    private Integer status;

    /** 角色编码列表（脱敏后展示） */
    private List<String> roles;

    /** 创建时间 */
    private Timestamp createTime;

    /** 更新时间 */
    private Timestamp updateTime;
}
