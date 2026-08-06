package com.finrpa.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.finrpa.auth.entity.RolePermissionEO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 角色-权限关联 Mapper
 *
 * <p>继承 MyBatis-Plus {@link BaseMapper}，提供按 roleId 查询关联、删除关联的自定义方法。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Mapper
public interface RolePermissionMapper extends BaseMapper<RolePermissionEO> {

    /**
     * 按角色 ID 查询已关联的权限 ID 列表
     *
     * @param roleId 角色业务 ID
     * @return 权限 ID 列表
     */
    @Select("SELECT permission_id FROM sys_role_permission WHERE role_id = #{roleId}")
    List<Long> selectPermissionIdsByRoleId(@Param("roleId") Long roleId);
}
