package com.finrpa.system.controller;

import com.finrpa.auth.service.PermissionService;
import com.finrpa.common.exception.BusinessException;
import com.finrpa.common.response.BaseResponse;
import com.finrpa.common.response.ErrorCode;
import com.finrpa.common.response.ResultUtils;
import com.finrpa.system.dto.response.SystemHealthVO;
import com.finrpa.system.service.SystemHealthService;
import com.finrpa.tenant.context.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统健康检查控制器（P2 OPS-1）
 *
 * <p>对外端点（实际访问路径前缀 {@code /api/system-health}）：
 * <ul>
 *   <li>GET /system-health —— 一键检测 DB / Redis / Python AI / MinIO 连通性</li>
 * </ul>
 * </p>
 *
 * <p>权限要求：调用方须为 org_admin / super_admin（由 Controller 内显式校验）。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Slf4j
@RestController
@RequestMapping("/system-health")
@Tag(name = "系统健康检查", description = "一键检测核心组件连通性（设置页安全策略）")
public class SystemHealthController {

    /** 系统健康检查服务 */
    @Resource
    private SystemHealthService systemHealthService;

    /** 权限服务（用于判断调用方是否为 org_admin / super_admin） */
    @Resource
    private PermissionService permissionService;

    // region 健康检查

    /**
     * 一键检测系统健康状态
     *
     * <p>聚合 DB / Redis / Python AI / MinIO 四类组件连通性检查，
     * 单组件失败不抛异常，仅在返回 VO 中标记 DOWN。</p>
     *
     * @return 健康检查结果
     */
    @GetMapping
    @Operation(summary = "一键系统健康检查", description = "检测 DB / Redis / Python AI / MinIO 连通性")
    public BaseResponse<SystemHealthVO> check() {
        checkAdminPermission();
        SystemHealthVO vo = systemHealthService.check();
        return ResultUtils.success(vo);
    }

    // endregion

    // region 私有方法

    /**
     * 权限校验：仅 org_admin / super_admin 可执行系统健康检查
     */
    private void checkAdminPermission() {
        String userIdStr = TenantContext.getUserId();
        if (userIdStr == null || userIdStr.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "当前请求未携带用户信息");
        }
        boolean isOrgAdmin = permissionService.isOrgAdmin(userIdStr);
        if (!isOrgAdmin) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限：仅组织管理员可执行系统健康检查");
        }
    }

    // endregion
}
