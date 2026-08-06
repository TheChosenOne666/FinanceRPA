package com.finrpa.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.finrpa.system.entity.SystemConfigEO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 系统配置 Mapper
 *
 * <p>继承 MyBatis-Plus {@link BaseMapper}，提供基础 CRUD 操作。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Mapper
public interface SystemConfigMapper extends BaseMapper<SystemConfigEO> {
}
