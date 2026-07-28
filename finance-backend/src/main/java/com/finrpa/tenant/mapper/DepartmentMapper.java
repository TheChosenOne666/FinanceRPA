package com.finrpa.tenant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.finrpa.tenant.entity.DepartmentEO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 部门表 Mapper 接口
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Mapper
public interface DepartmentMapper extends BaseMapper<DepartmentEO> {
}
