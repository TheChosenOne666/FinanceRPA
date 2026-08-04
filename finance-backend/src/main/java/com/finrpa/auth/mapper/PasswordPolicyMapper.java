package com.finrpa.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.finrpa.auth.entity.PasswordPolicyEO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 密码策略配置 Mapper
 *
 * <p>继承 MyBatis-Plus {@link BaseMapper}，提供基础 CRUD 操作。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Mapper
public interface PasswordPolicyMapper extends BaseMapper<PasswordPolicyEO> {
}
