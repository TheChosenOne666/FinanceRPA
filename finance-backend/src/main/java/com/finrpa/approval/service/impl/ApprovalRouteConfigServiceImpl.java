package com.finrpa.approval.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.finrpa.approval.dto.request.ApprovalRouteConfigAddRequest;
import com.finrpa.approval.dto.request.ApprovalRouteConfigQueryRequest;
import com.finrpa.approval.dto.request.ApprovalRouteConfigUpdateRequest;
import com.finrpa.approval.dto.response.ApprovalRouteConfigVO;
import com.finrpa.approval.entity.ApprovalRouteConfigEO;
import com.finrpa.approval.mapper.ApprovalRouteConfigMapper;
import com.finrpa.approval.service.ApprovalRouteConfigService;
import com.finrpa.auth.entity.UserEO;
import com.finrpa.auth.mapper.UserMapper;
import com.finrpa.common.exception.ThrowUtils;
import com.finrpa.common.response.ErrorCode;
import com.finrpa.tenant.entity.BusinessLineEO;
import com.finrpa.tenant.mapper.BusinessLineMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 审批人映射配置服务实现（P1 RSK-3）
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Slf4j
@Service
public class ApprovalRouteConfigServiceImpl implements ApprovalRouteConfigService {

    /** 审批人映射配置 Mapper */
    @Resource
    private ApprovalRouteConfigMapper approvalRouteConfigMapper;

    /** 用户 Mapper（联表填充审批人姓名） */
    @Resource
    private UserMapper userMapper;

    /** 业务线 Mapper（联表填充业务线名称） */
    @Resource
    private BusinessLineMapper businessLineMapper;

    // region 查询

    /**
     * 分页查询审批人映射配置（按当前请求组织过滤）
     *
     * @param queryRequest 查询请求
     * @param orgId        组织业务 ID
     * @return 分页结果
     */
    @Override
    public IPage<ApprovalRouteConfigVO> listConfigs(ApprovalRouteConfigQueryRequest queryRequest, Long orgId) {
        ThrowUtils.throwIf(orgId == null, ErrorCode.NOT_LOGIN_ERROR, "当前请求未携带组织信息");

        // 1. 构建查询条件（rpa_approval_route_config 已加入忽略清单，手动按 org_id 过滤）
        QueryWrapper<ApprovalRouteConfigEO> wrapper = new QueryWrapper<>();
        wrapper.eq("org_id", orgId);
        if (queryRequest != null) {
            if (queryRequest.getRiskLevel() != null && !queryRequest.getRiskLevel().isBlank()) {
                wrapper.eq("risk_level", queryRequest.getRiskLevel());
            }
            if (queryRequest.getBusinessLineId() != null) {
                wrapper.eq("business_line_id", queryRequest.getBusinessLineId());
            }
            if (queryRequest.getEnabled() != null) {
                wrapper.eq("enabled", queryRequest.getEnabled());
            }
        }
        wrapper.orderByAsc("risk_level", "business_line_id");

        // 2. 分页查询
        long current = queryRequest != null ? queryRequest.getCurrent() : 1;
        long size = queryRequest != null ? queryRequest.getPageSize() : 10;
        ThrowUtils.throwIf(size > 200, ErrorCode.PARAMS_ERROR, "每页数量不能超过 200");
        Page<ApprovalRouteConfigEO> page = new Page<>(current, size);
        IPage<ApprovalRouteConfigEO> eoPage = approvalRouteConfigMapper.selectPage(page, wrapper);

        // 3. 转换为 VO
        IPage<ApprovalRouteConfigVO> voPage = eoPage.convert(eo -> {
            ApprovalRouteConfigVO vo = new ApprovalRouteConfigVO();
            BeanUtils.copyProperties(eo, vo);
            return vo;
        });

        // 4. 批量填充 approverName + businessLineName（避免 N+1 查询）
        fillApproverNamesAndBizNames(voPage.getRecords(), orgId);
        return voPage;
    }

    /**
     * 根据风险等级 + 业务线查找审批人用户 ID
     *
     * @param orgId           组织业务 ID
     * @param riskLevel       风险等级
     * @param businessLineId  业务线业务 ID（可空）
     * @return 审批人用户业务 ID；找不到时返回 null
     */
    @Override
    public Long getApproverUserId(Long orgId, String riskLevel, Long businessLineId) {
        if (orgId == null || riskLevel == null) {
            return null;
        }
        // 1. 精确匹配：(orgId, riskLevel, businessLineId)
        if (businessLineId != null) {
            ApprovalRouteConfigEO exact = approvalRouteConfigMapper.selectExactMatch(orgId, riskLevel, businessLineId);
            if (exact != null && exact.getApproverUserId() != null) {
                log.debug("命中精确匹配审批人: orgId={}, riskLevel={}, businessLineId={}, approverId={}",
                        orgId, riskLevel, businessLineId, exact.getApproverUserId());
                return exact.getApproverUserId();
            }
        }
        // 2. 默认路由：(orgId, riskLevel, business_line_id IS NULL)
        ApprovalRouteConfigEO fallback = approvalRouteConfigMapper.selectDefaultRoute(orgId, riskLevel);
        if (fallback != null && fallback.getApproverUserId() != null) {
            log.debug("命中默认路由审批人: orgId={}, riskLevel={}, approverId={}",
                    orgId, riskLevel, fallback.getApproverUserId());
            return fallback.getApproverUserId();
        }
        // 3. 仍找不到：返回 null，审批单 approver_id 留空（由审批中心手动认领）
        log.warn("未找到审批人映射配置: orgId={}, riskLevel={}, businessLineId={}",
                orgId, riskLevel, businessLineId);
        return null;
    }

    // endregion

    // region 新增

    /**
     * 新增审批人映射配置
     *
     * @param orgId   组织业务 ID
     * @param request 新增请求
     * @return 新建的配置业务 ID
     */
    @Override
    public Long addConfig(Long orgId, ApprovalRouteConfigAddRequest request) {
        ThrowUtils.throwIf(orgId == null, ErrorCode.NOT_LOGIN_ERROR, "当前请求未携带组织信息");
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR, "新增请求不能为空");
        ThrowUtils.throwIf(request.getRiskLevel() == null || request.getRiskLevel().isBlank(),
                ErrorCode.PARAMS_ERROR, "风险等级不能为空");
        boolean validRiskLevel = "high".equalsIgnoreCase(request.getRiskLevel())
                || "critical".equalsIgnoreCase(request.getRiskLevel());
        ThrowUtils.throwIf(!validRiskLevel, ErrorCode.PARAMS_ERROR, "无效的风险等级: " + request.getRiskLevel());
        ThrowUtils.throwIf(request.getApproverUserId() == null,
                ErrorCode.PARAMS_ERROR, "审批人用户 ID 不能为空");

        // 1. 校验审批人用户存在
        UserEO approver = userMapper.selectByUserId(request.getApproverUserId());
        ThrowUtils.throwIf(approver == null, ErrorCode.NOT_FOUND_ERROR,
                "审批人用户不存在: " + request.getApproverUserId());

        // 2. 校验业务线存在（如传入）
        if (request.getBusinessLineId() != null) {
            LambdaQueryWrapper<BusinessLineEO> bizWrapper = new LambdaQueryWrapper<>();
            bizWrapper.eq(BusinessLineEO::getBusinessLineId, request.getBusinessLineId())
                    .eq(BusinessLineEO::getOrgId, orgId)
                    .eq(BusinessLineEO::getDeleted, 0);
            BusinessLineEO bizLine = businessLineMapper.selectOne(bizWrapper);
            ThrowUtils.throwIf(bizLine == null, ErrorCode.NOT_FOUND_ERROR,
                    "业务线不存在: " + request.getBusinessLineId());
        }

        // 3. 校验同一 (orgId, riskLevel, businessLineId) 唯一性
        QueryWrapper<ApprovalRouteConfigEO> dupWrapper = new QueryWrapper<>();
        dupWrapper.eq("org_id", orgId)
                .eq("risk_level", request.getRiskLevel());
        if (request.getBusinessLineId() == null) {
            dupWrapper.isNull("business_line_id");
        } else {
            dupWrapper.eq("business_line_id", request.getBusinessLineId());
        }
        ApprovalRouteConfigEO existing = approvalRouteConfigMapper.selectOne(dupWrapper);
        ThrowUtils.throwIf(existing != null, ErrorCode.OPERATION_ERROR,
                "该风险等级与业务线的审批人映射已存在");

        // 4. 构建实体
        ApprovalRouteConfigEO eo = new ApprovalRouteConfigEO();
        eo.setOrgId(orgId);
        eo.setRiskLevel(request.getRiskLevel());
        eo.setBusinessLineId(request.getBusinessLineId());
        eo.setApproverUserId(request.getApproverUserId());
        eo.setDepartmentId(request.getDepartmentId());
        eo.setDescription(request.getDescription());
        eo.setEnabled(request.getEnabled() != null ? request.getEnabled() : 1);

        // 5. 保存
        int rows = approvalRouteConfigMapper.insert(eo);
        ThrowUtils.throwIf(rows <= 0, ErrorCode.OPERATION_ERROR, "审批人映射保存失败");

        log.info("新增审批人映射: configId={}, orgId={}, riskLevel={}, businessLineId={}, approverId={}",
                eo.getConfigId(), orgId, request.getRiskLevel(), request.getBusinessLineId(),
                request.getApproverUserId());
        return eo.getConfigId();
    }

    // endregion

    // region 更新

    /**
     * 更新审批人映射配置
     *
     * @param configId 配置业务 ID
     * @param request  更新请求
     * @return 是否更新成功
     */
    @Override
    public boolean updateConfig(Long configId, ApprovalRouteConfigUpdateRequest request) {
        ThrowUtils.throwIf(configId == null, ErrorCode.PARAMS_ERROR, "配置 ID 不能为空");
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR, "更新请求不能为空");

        // 1. 查询原记录
        QueryWrapper<ApprovalRouteConfigEO> wrapper = new QueryWrapper<>();
        wrapper.eq("config_id", configId);
        ApprovalRouteConfigEO existing = approvalRouteConfigMapper.selectOne(wrapper);
        ThrowUtils.throwIf(existing == null, ErrorCode.NOT_FOUND_ERROR, "审批人映射配置不存在");

        // 2. 校验审批人用户存在（如传入）
        if (request.getApproverUserId() != null) {
            UserEO approver = userMapper.selectByUserId(request.getApproverUserId());
            ThrowUtils.throwIf(approver == null, ErrorCode.NOT_FOUND_ERROR,
                    "审批人用户不存在: " + request.getApproverUserId());
        }

        // 3. 构建更新字段
        com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<ApprovalRouteConfigEO> updateWrapper =
                new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<>();
        updateWrapper.eq("config_id", configId);
        if (request.getApproverUserId() != null) {
            updateWrapper.set("approver_user_id", request.getApproverUserId());
        }
        if (request.getDepartmentId() != null) {
            updateWrapper.set("department_id", request.getDepartmentId());
        }
        if (request.getDescription() != null) {
            updateWrapper.set("description", request.getDescription());
        }
        if (request.getEnabled() != null) {
            updateWrapper.set("enabled", request.getEnabled());
        }

        // 4. 执行更新
        int rows = approvalRouteConfigMapper.update(null, updateWrapper);
        ThrowUtils.throwIf(rows <= 0, ErrorCode.OPERATION_ERROR, "审批人映射更新失败");

        log.info("更新审批人映射: configId={}, approverId={}, enabled={}",
                configId, request.getApproverUserId(), request.getEnabled());
        return true;
    }

    // endregion

    // region 删除

    /**
     * 删除审批人映射配置
     *
     * @param configId 配置业务 ID
     * @return 是否删除成功
     */
    @Override
    public boolean deleteConfig(Long configId) {
        ThrowUtils.throwIf(configId == null, ErrorCode.PARAMS_ERROR, "配置 ID 不能为空");

        // 1. 查询原记录
        QueryWrapper<ApprovalRouteConfigEO> wrapper = new QueryWrapper<>();
        wrapper.eq("config_id", configId);
        ApprovalRouteConfigEO existing = approvalRouteConfigMapper.selectOne(wrapper);
        ThrowUtils.throwIf(existing == null, ErrorCode.NOT_FOUND_ERROR, "审批人映射配置不存在");

        // 2. 逻辑删除
        int rows = approvalRouteConfigMapper.deleteById(existing.getId());
        ThrowUtils.throwIf(rows <= 0, ErrorCode.OPERATION_ERROR, "审批人映射删除失败");

        log.info("删除审批人映射: configId={}, riskLevel={}, businessLineId={}",
                configId, existing.getRiskLevel(), existing.getBusinessLineId());
        return true;
    }

    // endregion

    // region 私有方法

    /**
     * 批量填充审批人姓名 + 业务线名称（避免 N+1 查询）
     *
     * @param records 配置 VO 列表（in-place 填充）
     * @param orgId   组织业务 ID（用于过滤业务线查询）
     */
    private void fillApproverNamesAndBizNames(List<ApprovalRouteConfigVO> records, Long orgId) {
        if (records == null || records.isEmpty()) {
            return;
        }

        // 1. 批量查询审批人姓名
        List<Long> userIds = records.stream()
                .map(ApprovalRouteConfigVO::getApproverUserId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, String> userIdToName = new HashMap<>();
        if (!userIds.isEmpty()) {
            List<UserEO> users = userMapper.selectByUserIds(userIds);
            if (users != null) {
                for (UserEO u : users) {
                    if (u.getUserId() != null) {
                        userIdToName.put(u.getUserId(), u.getRealName());
                    }
                }
            }
        }

        // 2. 批量查询业务线名称（按当前组织过滤）
        List<Long> businessLineIds = records.stream()
                .map(ApprovalRouteConfigVO::getBusinessLineId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, String> bizIdToName = new HashMap<>();
        if (!businessLineIds.isEmpty()) {
            LambdaQueryWrapper<BusinessLineEO> bizWrapper = new LambdaQueryWrapper<>();
            bizWrapper.eq(BusinessLineEO::getOrgId, orgId)
                    .eq(BusinessLineEO::getDeleted, 0)
                    .in(BusinessLineEO::getBusinessLineId, businessLineIds);
            List<BusinessLineEO> businessLines = businessLineMapper.selectList(bizWrapper);
            if (businessLines != null) {
                for (BusinessLineEO bl : businessLines) {
                    if (bl.getBusinessLineId() != null) {
                        bizIdToName.put(bl.getBusinessLineId(), bl.getBusinessLineName());
                    }
                }
            }
        }

        // 3. 填充 VO
        for (ApprovalRouteConfigVO vo : records) {
            if (vo.getApproverUserId() != null) {
                vo.setApproverName(userIdToName.get(vo.getApproverUserId()));
            }
            if (vo.getBusinessLineId() == null) {
                vo.setBusinessLineName("默认路由");
            } else {
                vo.setBusinessLineName(bizIdToName.get(vo.getBusinessLineId()));
            }
        }
    }

    // endregion
}
