package com.finrpa.tenant.interceptor;

import com.finrpa.tenant.constant.TenantConstant;
import com.finrpa.tenant.context.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.ModelAndView;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * TenantInterceptor 单元测试
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
class TenantInterceptorTest {

    /** 被测对象 */
    private final TenantInterceptor interceptor = new TenantInterceptor();

    @BeforeEach
    @AfterEach
    void cleanContext() {
        // 测试前后都清理 TenantContext，避免污染
        TenantContext.clear();
    }

    @Test
    @DisplayName("preHandle - request attribute 携带 orgId 时设置到 TenantContext")
    void preHandle_WithOrgId_SetsTenantContext() throws Exception {
        // 1. 准备 mock 请求（带 orgId 属性）
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getAttribute(TenantConstant.ORG_ID_REQUEST_ATTR)).thenReturn("org-001");

        // 2. 执行 preHandle
        boolean result = interceptor.preHandle(request, response, new Object());

        // 3. 验证返回 true 且 TenantContext 已设置
        assertThat(result).isTrue();
        assertThat(TenantContext.getOrgId()).isEqualTo("org-001");
    }

    @Test
    @DisplayName("preHandle - request attribute 无 orgId 时 TenantContext 保持空")
    void preHandle_WithoutOrgId_DoesNotSetTenantContext() throws Exception {
        // 1. 准备 mock 请求（不带 orgId 属性）
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getAttribute(TenantConstant.ORG_ID_REQUEST_ATTR)).thenReturn(null);

        // 2. 执行 preHandle
        boolean result = interceptor.preHandle(request, response, new Object());

        // 3. 验证返回 true 但 TenantContext 为空（不抛异常）
        assertThat(result).isTrue();
        assertThat(TenantContext.getOrgId()).isNull();
    }

    @Test
    @DisplayName("preHandle - request attribute 为空字符串时 TenantContext 保持空")
    void preHandle_WithEmptyOrgId_DoesNotSetTenantContext() throws Exception {
        // 1. 准备 mock 请求（orgId 为空字符串）
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getAttribute(TenantConstant.ORG_ID_REQUEST_ATTR)).thenReturn("");

        // 2. 执行 preHandle
        boolean result = interceptor.preHandle(request, response, new Object());

        // 3. 验证返回 true 但 TenantContext 为空
        assertThat(result).isTrue();
        assertThat(TenantContext.getOrgId()).isNull();
    }

    @Test
    @DisplayName("afterCompletion - 清理 TenantContext")
    void afterCompletion_ClearsTenantContext() throws Exception {
        // 1. 预先设置 TenantContext
        TenantContext.setOrgId("org-002");

        // 2. 执行 afterCompletion
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        interceptor.afterCompletion(request, response, new Object(), null);

        // 3. 验证 TenantContext 已清理
        assertThat(TenantContext.getOrgId()).isNull();
    }

    @Test
    @DisplayName("afterCompletion - 即使有异常也清理 TenantContext")
    void afterCompletion_WithException_ClearsTenantContext() throws Exception {
        // 1. 预先设置 TenantContext
        TenantContext.setOrgId("org-003");

        // 2. 执行 afterCompletion（带异常）
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        Exception ex = new RuntimeException("测试异常");
        interceptor.afterCompletion(request, response, new Object(), ex);

        // 3. 验证 TenantContext 已清理
        assertThat(TenantContext.getOrgId()).isNull();
    }

    @Test
    @DisplayName("postHandle - 不抛异常即可")
    void postHandle_DoesNotThrowException() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        ModelAndView modelAndView = new ModelAndView();

        // postHandle 应不抛异常
        interceptor.postHandle(request, response, new Object(), modelAndView);
    }
}
