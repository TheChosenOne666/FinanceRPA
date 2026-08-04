package com.finrpa.auth.controller;

import com.finrpa.auth.dto.request.LoginPolicyUpdateRequest;
import com.finrpa.auth.dto.response.LoginPolicyVO;
import com.finrpa.auth.service.LoginPolicyService;
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
 * 登录安全策略配置控制器（P2 SEC-2）
 *
 * <p>对外端点（实际访问路径前缀 {@code /api/login-policy}）：
 * <ul>
 *   <li>GET /login-policy —— 查询当前登录策略</li>
 *   <li>PUT /login-policy —— 更新登录策略</li>
 * </ul>
 * </p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Slf4j
@RestController
@RequestMapping("/login-policy")
@Tag(name = "登录安全策略配置", description = "账号锁定 / IP 白黑名单 / 并发登录 / 空闲超时（设置页安全策略）")
public class LoginPolicyController {

    /** 登录策略服务 */
    @Resource
    private LoginPolicyService loginPolicyService;

    // region 查询

    /**
     * 查询当前登录策略
     *
     * @return 登录策略 VO（策略被禁用时返回 null）
     */
    @GetMapping
    @Operation(summary = "查询登录策略", description = "获取当前启用的登录安全策略配置")
    public BaseResponse<LoginPolicyVO> getPolicy() {
        LoginPolicyVO vo = loginPolicyService.getActivePolicy();
        return ResultUtils.success(vo);
    }

    // endregion

    // region 更新

    /**
     * 更新登录策略
     *
     * @param request 更新请求
     * @return 更新后的策略 VO
     */
    @PutMapping
    @Operation(summary = "更新登录策略", description = "更新账号锁定 / IP 白黑名单 / 并发登录 / 空闲超时配置")
    public BaseResponse<LoginPolicyVO> updatePolicy(@RequestBody LoginPolicyUpdateRequest request) {
        LoginPolicyVO vo = loginPolicyService.updatePolicy(request);
        return ResultUtils.success(vo);
    }

    // endregion
}
