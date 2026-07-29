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
}