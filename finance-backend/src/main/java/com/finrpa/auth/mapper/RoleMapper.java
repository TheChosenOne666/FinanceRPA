package com.finrpa.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.finrpa.auth.entity.RoleEO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface RoleMapper extends BaseMapper<RoleEO> {

    @Select("SELECT r.* FROM finrpa.sys_role r " +
            "JOIN finrpa.sys_user_role ur ON r.role_id = ur.role_id " +
            "WHERE ur.user_id = #{userId}::uuid AND r.deleted = 0")
    List<RoleEO> selectByUserId(@Param("userId") String userId);

    @Select("SELECT * FROM finrpa.sys_role WHERE role_code = #{roleCode} AND deleted = 0")
    RoleEO selectByRoleCode(@Param("roleCode") String roleCode);
}