package com.finrpa.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.finrpa.auth.entity.UserRoleEO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 用户-角色关联表 Mapper 接口
 *
 * <p>M7.6 起扩展为三维度 RBAC：user × department × business_line × role。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Mapper
public interface UserRoleMapper extends BaseMapper<UserRoleEO> {

    /**
     * 根据用户业务 ID 查询其所有三维度关联（按 id 升序）
     *
     * @param userId 用户业务 ID（雪花算法 ID）
     * @return 用户-角色关联列表；无关联时返回空集合
     */
    @Select("SELECT * FROM finrpa.sys_user_role WHERE user_id = #{userId} ORDER BY id ASC")
    List<UserRoleEO> selectByUserId(@Param("userId") Long userId);
}