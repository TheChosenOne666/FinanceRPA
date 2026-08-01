package com.finrpa.approval.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.finrpa.approval.entity.ApprovalRequestEO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 审批请求数据库操作 Mapper
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Mapper
public interface ApprovalRequestMapper extends BaseMapper<ApprovalRequestEO> {
}
