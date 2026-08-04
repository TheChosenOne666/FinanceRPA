package com.finrpa.auth.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.finrpa.auth.dto.request.SessionQueryRequest;
import com.finrpa.auth.dto.response.SessionVO;
import com.finrpa.auth.service.PermissionService;
import com.finrpa.auth.service.SessionService;
import com.finrpa.common.exception.BusinessException;
import com.finrpa.common.response.BaseResponse;
import com.finrpa.common.response.ErrorCode;
import com.finrpa.common.response.ResultUtils;
import com.finrpa.tenant.context.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 在线会话管理控制器（P2 SEC-3）
 *
 * <p>对外端点（实际访问路径前缀 {@code /api/sessions}）：
 * <ul>
 *   <li>GET /sessions —— 分页查询在线会话列表（按 userId / username 筛选）</li>
 *   <li>DELETE /sessions/{sessionId} —— 踢人下线</li>
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
@RequestMapping("/sessions")
@Tag(name = "在线会话管理", description = "查询在线会话 / 踢人下线（设置页安全策略）")
public class SessionController {

    /** 会话管理服务 */
    @Resource
    private SessionService sessionService;

    /** 权限服务（用于判断调用方是否为 org_admin / super_admin） */
    @Resource
    private PermissionService permissionService;

    // region 查询

    /**
     * 分页查询在线会话列表
     *
     * @param queryRequest 查询请求
     * @return 分页结果
     */
    @GetMapping
    @Operation(summary = "在线会话列表", description = "分页查询当前在线会话，可按 userId / username 筛选")
    public BaseResponse<IPage<SessionVO>> listSessions(SessionQueryRequest queryRequest) {
        checkAdminPermission();
        IPage<SessionVO> page = sessionService.listSessions(queryRequest);
        return ResultUtils.success(page);
    }

    // endregion

    // region 踢人下线

    /**
     * 踢人下线（按 sessionId 销毁会话）
     *
     * @param sessionId 会话 ID
     * @return 操作结果
     */
    @DeleteMapping("/{sessionId}")
    @Operation(summary = "踢人下线", description = "按 sessionId 销毁在线会话，将对应 token 加入黑名单")
    public BaseResponse<Boolean> killSession(@PathVariable String sessionId) {
        checkAdminPermission();
        boolean success = sessionService.killSession(sessionId);
        if (!success) {
            throw new BusinessException(ErrorCode.SESSION_NOT_FOUND, "会话不存在或已下线");
        }
        return ResultUtils.success(true);
    }

    // endregion

    // region 私有方法

    /**
     * 权限校验：仅 org_admin / super_admin 可访问会话管理
     */
    private void checkAdminPermission() {
        String userIdStr = TenantContext.getUserId();
        if (userIdStr == null || userIdStr.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "当前请求未携带用户信息");
        }
        boolean isOrgAdmin = permissionService.isOrgAdmin(userIdStr);
        if (!isOrgAdmin) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限：仅组织管理员可管理在线会话");
        }
    }

    // endregion
}
