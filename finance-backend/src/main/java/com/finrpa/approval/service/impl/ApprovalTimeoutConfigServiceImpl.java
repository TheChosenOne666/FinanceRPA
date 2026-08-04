package com.finrpa.approval.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.finrpa.approval.constant.ApprovalConstant;
import com.finrpa.approval.dto.request.ApprovalTimeoutConfigUpdateRequest;
import com.finrpa.approval.dto.response.ApprovalTimeoutConfigVO;
import com.finrpa.approval.entity.ApprovalTimeoutConfigEO;
import com.finrpa.approval.mapper.ApprovalTimeoutConfigMapper;
import com.finrpa.approval.service.ApprovalTimeoutConfigService;
import com.finrpa.common.exception.ThrowUtils;
import com.finrpa.common.response.ErrorCode;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 审批超时阈值配置服务实现（P1 RSK-1）
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Slf4j
@Service
public class ApprovalTimeoutConfigServiceImpl implements ApprovalTimeoutConfigService {

    /** 审批超时阈值配置 Mapper */
    @Resource
    private ApprovalTimeoutConfigMapper approvalTimeoutConfigMapper;

    // region 查询

    /**
     * 查询全部超时配置（设置页展示用）
     *
     * @return 超时配置列表
     */
    @Override
    public List<ApprovalTimeoutConfigVO> listAll() {
        // 1. 查询全部未删除的配置，按风险等级升序
        QueryWrapper<ApprovalTimeoutConfigEO> wrapper = new QueryWrapper<>();
        wrapper.orderByAsc("risk_level");
        List<ApprovalTimeoutConfigEO> list = approvalTimeoutConfigMapper.selectList(wrapper);

        // 2. 转换为 VO
        return list.stream().map(eo -> {
            ApprovalTimeoutConfigVO vo = new ApprovalTimeoutConfigVO();
            BeanUtils.copyProperties(eo, vo);
            return vo;
        }).collect(Collectors.toList());
    }

    /**
     * 根据风险等级获取超时分钟数
     *
     * <p>配置不存在或被禁用时，回退到 {@link ApprovalConstant} 默认值。</p>
     *
     * @param riskLevel 风险等级
     * @return 超时分钟数
     */
    @Override
    public long getTimeoutMinutesByRiskLevel(String riskLevel) {
        // 1. 查询启用状态的配置
        QueryWrapper<ApprovalTimeoutConfigEO> wrapper = new QueryWrapper<>();
        wrapper.eq("risk_level", riskLevel)
                .eq("enabled", 1);
        ApprovalTimeoutConfigEO eo = approvalTimeoutConfigMapper.selectOne(wrapper);

        // 2. 配置存在且启用：返回配置值
        if (eo != null && eo.getTimeoutMinutes() != null && eo.getTimeoutMinutes() > 0) {
            return eo.getTimeoutMinutes();
        }

        // 3. 配置缺失或被禁用：回退到常量默认值
        log.debug("审批超时配置缺失或被禁用，回退到默认值: riskLevel={}", riskLevel);
        if ("critical".equalsIgnoreCase(riskLevel)) {
            return ApprovalConstant.CRITICAL_APPROVAL_TIMEOUT_MINUTES;
        }
        if ("high".equalsIgnoreCase(riskLevel)) {
            return ApprovalConstant.HIGH_APPROVAL_TIMEOUT_MINUTES;
        }
        return ApprovalConstant.DEFAULT_APPROVAL_TIMEOUT_MINUTES;
    }

    // endregion

    // region 更新

    /**
     * 更新指定风险等级的超时配置
     *
     * @param riskLevel 风险等级（high / critical）
     * @param request   更新请求
     * @return 更新后的配置 VO
     */
    @Override
    public ApprovalTimeoutConfigVO updateConfig(String riskLevel, ApprovalTimeoutConfigUpdateRequest request) {
        // 1. 参数校验
        ThrowUtils.throwIf(riskLevel == null || riskLevel.isBlank(),
                ErrorCode.PARAMS_ERROR, "风险等级不能为空");
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR, "更新请求不能为空");
        boolean validRiskLevel = "high".equalsIgnoreCase(riskLevel)
                || "critical".equalsIgnoreCase(riskLevel);
        ThrowUtils.throwIf(!validRiskLevel, ErrorCode.PARAMS_ERROR, "无效的风险等级: " + riskLevel);
        ThrowUtils.throwIf(request.getTimeoutMinutes() == null || request.getTimeoutMinutes() <= 0,
                ErrorCode.PARAMS_ERROR, "超时分钟数必须大于 0");
        ThrowUtils.throwIf(request.getTimeoutMinutes() > 1440,
                ErrorCode.PARAMS_ERROR, "超时分钟数不能超过 1440 分钟（24 小时）");

        // 2. 查询原记录
        QueryWrapper<ApprovalTimeoutConfigEO> wrapper = new QueryWrapper<>();
        wrapper.eq("risk_level", riskLevel);
        ApprovalTimeoutConfigEO existing = approvalTimeoutConfigMapper.selectOne(wrapper);
        ThrowUtils.throwIf(existing == null, ErrorCode.NOT_FOUND_ERROR, "超时配置不存在: " + riskLevel);

        // 3. 构建更新字段
        UpdateWrapper<ApprovalTimeoutConfigEO> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("risk_level", riskLevel)
                .set("timeout_minutes", request.getTimeoutMinutes());
        if (request.getDescription() != null) {
            updateWrapper.set("description", request.getDescription());
        }
        if (request.getEnabled() != null) {
            updateWrapper.set("enabled", request.getEnabled());
        }

        // 4. 执行更新
        int rows = approvalTimeoutConfigMapper.update(null, updateWrapper);
        ThrowUtils.throwIf(rows <= 0, ErrorCode.OPERATION_ERROR, "超时配置更新失败");

        // 5. 重新查询返回
        ApprovalTimeoutConfigEO updated = approvalTimeoutConfigMapper.selectOne(wrapper);
        ApprovalTimeoutConfigVO vo = new ApprovalTimeoutConfigVO();
        BeanUtils.copyProperties(updated, vo);

        log.info("更新审批超时配置: riskLevel={}, timeoutMinutes={}min, enabled={}",
                riskLevel, request.getTimeoutMinutes(), request.getEnabled());
        return vo;
    }

    // endregion
}
