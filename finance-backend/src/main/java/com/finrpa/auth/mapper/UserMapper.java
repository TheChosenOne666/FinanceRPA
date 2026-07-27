package com.finrpa.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.finrpa.auth.entity.UserEO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper extends BaseMapper<UserEO> {

    @Select("SELECT * FROM finrpa.sys_user WHERE username = #{username} AND deleted = 0")
    UserEO selectByUsername(@Param("username") String username);

    @Select("SELECT * FROM finrpa.sys_user WHERE user_id = #{userId}::uuid AND deleted = 0")
    UserEO selectByUserId(@Param("userId") String userId);
}