package com.finrpa.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.finrpa.auth.entity.UserRoleEO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户-角色关联表 Mapper 接口
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Mapper
public interface UserRoleMapper extends BaseMapper<UserRoleEO> {
}