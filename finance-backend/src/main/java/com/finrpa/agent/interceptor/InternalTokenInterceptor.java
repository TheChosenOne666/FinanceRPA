package com.finrpa.agent.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finrpa.ai.config.AiServiceProperties;
import com.finrpa.common.response.BaseResponse;
import com.finrpa.common.response.ErrorCode;
import com.finrpa.common.response.ResultUtils;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

/**
 * 内部 API 鉴权拦截器
 *
 * <p>拦截 {@code /internal/**} 路径，校验 {@code X-Internal-Token} Header 是否匹配配置的共享密钥。
 * 仅 Docker 内网可达，不对外暴露。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Slf4j
@Component
public class InternalTokenInterceptor implements HandlerInterceptor {

    /** AI 服务配置属性（复用 internal-token 配置） */
    @Resource
    private AiServiceProperties aiServiceProperties;

    /** JSON 序列化工具 */
    @Resource
    private ObjectMapper objectMapper;

    /**
     * 请求处理前：校验 X-Internal-Token Header
     *
     * @param request  HTTP 请求
     * @param response HTTP 响应
     * @param handler  处理器
     * @return true-通过；false-拒绝
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        // 1. 提取 X-Internal-Token Header
        String token = request.getHeader("X-Internal-Token");

        // 2. 校验 token
        if (!StringUtils.hasText(token) || !token.equals(aiServiceProperties.getInternalToken())) {
            log.warn("内部 API 鉴权失败: path={}, remoteAddr={}",
                    request.getRequestURI(), request.getRemoteAddr());
            // 3. 返回 401 错误
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            BaseResponse<?> body = ResultUtils.error(ErrorCode.FORBIDDEN_ERROR, "内部 API 鉴权失败");
            response.getWriter().write(objectMapper.writeValueAsString(body));
            return false;
        }

        return true;
    }
}
