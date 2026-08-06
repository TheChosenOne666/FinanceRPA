package com.finrpa.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.finrpa.auth.entity.PermissionEO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 权限点 Mapper
 *
 * <p>继承 MyBatis-Plus {@link BaseMapper}，并提供按 roleId 关联查询权限列表的自定义方法。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Mapper
public interface PermissionMapper extends BaseMapper<PermissionEO> {

    /**
     * 按角色 ID 查询已关联的权限点列表（JOIN sys_role_permission）
     *
     * @param roleId 角色业务 ID
     * @return 权限点列表
     */
    @Select("SELECT p.* FROM sys_permission p " +
            "INNER JOIN sys_role_permission rp ON p.permission_id = rp.permission_id " +
            "WHERE rp.role_id = #{roleId} AND p.status = 1 AND p.deleted = 0 " +
            "ORDER BY p.sort_order ASC, p.permission_id ASC")
    List<PermissionEO> selectByRoleId(@Param("roleId") Long roleId);

    /**
     * 按 roleCode 查询已关联的权限编码列表（JOIN sys_role + sys_role_permission）
     *
     * <p>用于 {@code PermissionServiceImpl.getPermissionsByRole} 的 DB 化分支。</p>
     *
     * @param roleCode 角色编码
     * @return 权限编码列表
     */
    @Select("SELECT p.permission_code FROM sys_permission p " +
            "INNER JOIN sys_role_permission rp ON p.permission_id = rp.permission_id " +
            "INNER JOIN sys_role r ON rp.role_id = r.role_id " +
            "WHERE r.role_code = #{roleCode} AND p.status = 1 AND p.deleted = 0 AND r.deleted = 0")
    List<String> selectPermissionCodesByRoleCode(@Param("roleCode") String roleCode);
}
