package com.finrpa.approval.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.finrpa.approval.entity.ApprovalRouteConfigEO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 审批人映射配置 Mapper
 *
 * <p>继承 MyBatis-Plus {@link BaseMapper}，提供基础 CRUD 操作；
 * 额外提供按 {@code (org_id, risk_level, business_line_id)} 精确匹配 / 默认路由 fallback 查询。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Mapper
public interface ApprovalRouteConfigMapper extends BaseMapper<ApprovalRouteConfigEO> {

    /**
     * 按组织 + 风险等级 + 业务线精确匹配审批人（启用状态，未删除）
     *
     * @param orgId           组织业务 ID
     * @param riskLevel       风险等级
     * @param businessLineId  业务线业务 ID
     * @return 审批人映射配置；不存在时返回 null
     */
    @Select("SELECT * FROM finrpa.rpa_approval_route_config "
            + "WHERE org_id = #{orgId} AND risk_level = #{riskLevel} "
            + "AND business_line_id = #{businessLineId} "
            + "AND enabled = 1 AND deleted = 0")
    ApprovalRouteConfigEO selectExactMatch(@Param("orgId") Long orgId,
                                            @Param("riskLevel") String riskLevel,
                                            @Param("businessLineId") Long businessLineId);

    /**
     * 按组织 + 风险等级查找默认路由审批人（business_line_id IS NULL，启用状态，未删除）
     *
     * @param orgId     组织业务 ID
     * @param riskLevel 风险等级
     * @return 审批人映射配置；不存在时返回 null
     */
    @Select("SELECT * FROM finrpa.rpa_approval_route_config "
            + "WHERE org_id = #{orgId} AND risk_level = #{riskLevel} "
            + "AND business_line_id IS NULL "
            + "AND enabled = 1 AND deleted = 0")
    ApprovalRouteConfigEO selectDefaultRoute(@Param("orgId") Long orgId,
                                              @Param("riskLevel") String riskLevel);
}
