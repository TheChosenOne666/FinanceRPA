package com.finrpa.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.finrpa.auth.entity.LoginPolicyEO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 登录安全策略配置 Mapper
 *
 * <p>继承 MyBatis-Plus {@link BaseMapper}，提供基础 CRUD 操作。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Mapper
public interface LoginPolicyMapper extends BaseMapper<LoginPolicyEO> {
}
