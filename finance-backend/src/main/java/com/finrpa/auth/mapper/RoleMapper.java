package com.finrpa.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.finrpa.auth.entity.RoleEO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 角色表 Mapper 接口
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Mapper
public interface RoleMapper extends BaseMapper<RoleEO> {

    /**
     * 根据用户业务 ID 查询其关联的未删除角色列表
     *
     * @param userId 用户业务 ID（雪花算法 ID）
     * @return 角色实体列表；无关联角色时返回空集合
     */
    @Select("SELECT r.* FROM finrpa.sys_role r " +
            "JOIN finrpa.sys_user_role ur ON r.role_id = ur.role_id " +
            "WHERE ur.user_id = #{userId} AND r.deleted = 0")
    List<RoleEO> selectByUserId(@Param("userId") Long userId);

    /**
     * 根据角色编码查询未删除的角色
     *
     * @param roleCode 角色编码
     * @return 角色实体；不存在时返回 null
     */
    @Select("SELECT * FROM finrpa.sys_role WHERE role_code = #{roleCode} AND deleted = 0")
    RoleEO selectByRoleCode(@Param("roleCode") String roleCode);

    /**
     * 根据角色业务 ID 列表批量查询未删除的角色（用于分配角色时校验存在性）
     *
     * @param roleIds 角色业务 ID 列表
     * @return 角色实体列表
     */
    @Select("<script>" +
            "SELECT role_id, role_code, role_name FROM finrpa.sys_role " +
            "WHERE deleted = 0 AND role_id IN " +
            "<foreach item='id' collection='roleIds' open='(' separator=',' close=')'>" +
            "#{id}" +
            "</foreach>" +
            "</script>")
    List<RoleEO> selectByRoleIds(@Param("roleIds") List<Long> roleIds);

    /**
     * 查询全部未删除角色（按 id 升序）
     *
     * <p>sys_role 表已在 TenantConstant.IGNORED_TABLES 中，super_admin 可看全部。
     * org_admin 仅能看本组织的角色 + 全局内置角色（org_id IS NULL）。</p>
     *
     * @return 角色实体列表
     */
    @Select("SELECT * FROM finrpa.sys_role WHERE deleted = 0 ORDER BY id ASC")
    List<RoleEO> selectAll();
}