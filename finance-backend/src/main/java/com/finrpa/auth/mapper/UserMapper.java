package com.finrpa.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.finrpa.auth.entity.UserEO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

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
     * @param userId 用户业务 ID（雪花算法 ID）
     * @return 用户实体；不存在时返回 null
     */
    @Select("SELECT * FROM finrpa.sys_user WHERE user_id = #{userId} AND deleted = 0")
    UserEO selectByUserId(@Param("userId") Long userId);

    /**
     * 根据用户业务 ID 列表批量查询未删除的用户（用于批量填充 userName / createUser）
     *
     * @param userIds 用户业务 ID 列表
     * @return 用户实体列表
     */
    @Select("<script>" +
            "SELECT user_id, real_name FROM finrpa.sys_user " +
            "WHERE deleted = 0 AND user_id IN " +
            "<foreach item='id' collection='userIds' open='(' separator=',' close=')'>" +
            "#{id}" +
            "</foreach>" +
            "</script>")
    List<UserEO> selectByUserIds(@Param("userIds") List<Long> userIds);
}