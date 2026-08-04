package com.finrpa.approval.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.finrpa.approval.entity.ApprovalTimeoutConfigEO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 审批超时阈值配置 Mapper
 *
 * <p>继承 MyBatis-Plus {@link BaseMapper}，提供基础 CRUD 操作。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Mapper
public interface ApprovalTimeoutConfigMapper extends BaseMapper<ApprovalTimeoutConfigEO> {
}
