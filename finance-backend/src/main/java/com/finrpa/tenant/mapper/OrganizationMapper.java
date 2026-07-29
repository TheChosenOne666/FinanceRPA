package com.finrpa.tenant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.finrpa.tenant.entity.OrganizationEO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 组织表 Mapper 接口
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Mapper
public interface OrganizationMapper extends BaseMapper<OrganizationEO> {

    /**
     * 根据组织业务 ID 查询未删除的组织
     *
     * @param orgId 组织业务 ID（雪花算法 ID）
     * @return 组织实体；不存在时返回 null
     */
    // enterprise_organization 表本身不在 TenantLineHandler 过滤范围内，需手动按 org_id 过滤
    @Select("SELECT * FROM finrpa.enterprise_organization WHERE org_id = #{orgId} AND deleted = 0")
    OrganizationEO selectByOrgId(@Param("orgId") Long orgId);
}
