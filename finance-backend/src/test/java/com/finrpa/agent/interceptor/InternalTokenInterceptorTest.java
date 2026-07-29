package com.finrpa.agent.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finrpa.ai.config.AiServiceProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * 内部 API 鉴权拦截器单元测试
 *
 * <p>覆盖 X-Internal-Token Header 校验的通过/拒绝场景。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
class InternalTokenInterceptorTest {

    /** 配置的共享密钥 */
    private static final String VALID_TOKEN = "finrpa-internal-secret";

    /** 被测对象 */
    private InternalTokenInterceptor interceptor;

    /** mock 依赖 */
    private AiServiceProperties aiServiceProperties;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        // 1. 创建被测对象与 mock 依赖
        interceptor = new InternalTokenInterceptor();
        aiServiceProperties = new AiServiceProperties();
        objectMapper = new ObjectMapper();

        // 2. 注入依赖
        ReflectionTestUtils.setField(interceptor, "aiServiceProperties", aiServiceProperties);
        ReflectionTestUtils.setField(interceptor, "objectMapper", objectMapper);
    }

    @Test
    @DisplayName("preHandle - 携带正确 token 返回 true")
    void preHandle_WithValidToken_ReturnsTrue() throws Exception {
        // 1. 准备 mock 请求（携带正确 token）
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getHeader("X-Internal-Token")).thenReturn(VALID_TOKEN);

        // 2. 执行 preHandle
        boolean result = interceptor.preHandle(request, response, new Object());

        // 3. 验证返回 true（放行）
        assertThat(result).isTrue();
        // 4. 验证未设置错误状态码
        verify(response, never()).setStatus(anyInt());
    }

    @Test
    @DisplayName("preHandle - 未携带 token 返回 false 并设置 401")
    void preHandle_WithoutToken_ReturnsFalseAndSets401() throws Exception {
        // 1. 准备 mock 请求（无 token）
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getHeader("X-Internal-Token")).thenReturn(null);
        when(request.getRequestURI()).thenReturn("/api/internal/tasks/1/state");
        when(request.getRemoteAddr()).thenReturn("172.18.0.5");
        StringWriter writer = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(writer));

        // 2. 执行 preHandle
        boolean result = interceptor.preHandle(request, response, new Object());

        // 3. 验证返回 false（拒绝）
        assertThat(result).isFalse();
        // 4. 验证设置 401 状态码
        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    @DisplayName("preHandle - 携带错误 token 返回 false 并设置 401")
    void preHandle_WithWrongToken_ReturnsFalseAndSets401() throws Exception {
        // 1. 准备 mock 请求（错误 token）
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getHeader("X-Internal-Token")).thenReturn("wrong-token");
        when(request.getRequestURI()).thenReturn("/api/internal/tasks/1/state");
        when(request.getRemoteAddr()).thenReturn("172.18.0.5");
        StringWriter writer = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(writer));

        // 2. 执行 preHandle
        boolean result = interceptor.preHandle(request, response, new Object());

        // 3. 验证返回 false（拒绝）
        assertThat(result).isFalse();
        // 4. 验证设置 401 状态码
        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    @DisplayName("preHandle - 携带空字符串 token 返回 false")
    void preHandle_WithEmptyToken_ReturnsFalse() throws Exception {
        // 1. 准备 mock 请求（空字符串 token）
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getHeader("X-Internal-Token")).thenReturn("");
        when(request.getRequestURI()).thenReturn("/api/internal/tasks/1/state");
        when(request.getRemoteAddr()).thenReturn("172.18.0.5");
        StringWriter writer = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(writer));

        // 2. 执行 preHandle
        boolean result = interceptor.preHandle(request, response, new Object());

        // 3. 验证返回 false（拒绝）
        assertThat(result).isFalse();
        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }
}
