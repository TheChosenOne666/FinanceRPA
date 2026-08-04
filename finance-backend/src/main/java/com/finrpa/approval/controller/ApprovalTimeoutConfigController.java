package com.finrpa.approval.controller;

import com.finrpa.approval.dto.request.ApprovalTimeoutConfigUpdateRequest;
import com.finrpa.approval.dto.response.ApprovalTimeoutConfigVO;
import com.finrpa.approval.service.ApprovalTimeoutConfigService;
import com.finrpa.common.response.BaseResponse;
import com.finrpa.common.response.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 审批超时阈值配置控制器（P1 RSK-1）
 *
 * <p>对外端点（实际访问路径前缀 {@code /api/approval-timeout}）：
 * <ul>
 *   <li>GET /approval-timeout —— 查询全部超时配置（设置页风控配置区块展示）</li>
 *   <li>PUT /approval-timeout/{riskLevel} —— 更新指定风险等级的超时分钟数</li>
 * </ul>
 * </p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Slf4j
@RestController
@RequestMapping("/approval-timeout")
@Tag(name = "审批超时阈值配置", description = "按风险等级配置审批超时分钟数（设置页风控配置）")
public class ApprovalTimeoutConfigController {

    /** 审批超时阈值配置服务 */
    @Resource
    private ApprovalTimeoutConfigService approvalTimeoutConfigService;

    // region 查询

    /**
     * 查询全部超时配置
     *
     * @return 超时配置列表
     */
    @GetMapping
    @Operation(summary = "超时配置列表", description = "查询全部审批超时阈值配置")
    public BaseResponse<List<ApprovalTimeoutConfigVO>> listAll() {
        List<ApprovalTimeoutConfigVO> list = approvalTimeoutConfigService.listAll();
        return ResultUtils.success(list);
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
    @PutMapping("/{riskLevel}")
    @Operation(summary = "更新超时配置", description = "按风险等级更新审批超时分钟数")
    public BaseResponse<ApprovalTimeoutConfigVO> updateConfig(
            @PathVariable String riskLevel,
            @RequestBody ApprovalTimeoutConfigUpdateRequest request) {
        ApprovalTimeoutConfigVO vo = approvalTimeoutConfigService.updateConfig(riskLevel, request);
        return ResultUtils.success(vo);
    }

    // endregion
}
