package com.finrpa.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.finrpa.auth.entity.UserEO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 用户表 Mapper 接口
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Mapper
public interface UserMapper extends BaseMapper<UserEO> {

    /**
     * 根据用户名查询未删除的用户
     *
     * @param username 用户名
     * @return 用户实体；不存在时返回 null
     */
    @Select("SELECT * FROM finrpa.sys_user WHERE username = #{username} AND deleted = 0")
    UserEO selectByUsername(@Param("username") String username);

    /**
     * 根据用户业务 ID 查询未删除的用户
     *
     * @param userId 用户业务 ID
     * @return 用户实体；不存在时返回 null
     */
    // 使用 ::uuid 将传入字符串转换为 PostgreSQL 的 UUID 类型
    @Select("SELECT * FROM finrpa.sys_user WHERE user_id = #{userId}::uuid AND deleted = 0")
    UserEO selectByUserId(@Param("userId") String userId);
}