package com.finrpa.auth.controller;

import com.finrpa.auth.dto.request.PasswordPolicyUpdateRequest;
import com.finrpa.auth.dto.response.PasswordPolicyVO;
import com.finrpa.auth.service.PasswordPolicyService;
import com.finrpa.common.response.BaseResponse;
import com.finrpa.common.response.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 密码策略配置控制器（P2 SEC-1）
 *
 * <p>对外端点（实际访问路径前缀 {@code /api/password-policy}）：
 * <ul>
 *   <li>GET /password-policy —— 查询当前密码策略</li>
 *   <li>PUT /password-policy —— 更新密码策略</li>
 * </ul>
 * </p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Slf4j
@RestController
@RequestMapping("/password-policy")
@Tag(name = "密码策略配置", description = "密码强度规则 / 过期天数 / 历史密码检查（设置页安全策略）")
public class PasswordPolicyController {

    /** 密码策略服务 */
    @Resource
    private PasswordPolicyService passwordPolicyService;

    // region 查询

    /**
     * 查询当前密码策略
     *
     * @return 密码策略 VO（策略被禁用时返回 null）
     */
    @GetMapping
    @Operation(summary = "查询密码策略", description = "获取当前启用的密码策略配置")
    public BaseResponse<PasswordPolicyVO> getPolicy() {
        PasswordPolicyVO vo = passwordPolicyService.getActivePolicy();
        return ResultUtils.success(vo);
    }

    // endregion

    // region 更新

    /**
     * 更新密码策略
     *
     * @param request 更新请求
     * @return 更新后的策略 VO
     */
    @PutMapping
    @Operation(summary = "更新密码策略", description = "更新密码强度规则 / 过期天数 / 历史密码检查")
    public BaseResponse<PasswordPolicyVO> updatePolicy(@RequestBody PasswordPolicyUpdateRequest request) {
        PasswordPolicyVO vo = passwordPolicyService.updatePolicy(request);
        return ResultUtils.success(vo);
    }

    // endregion
}
