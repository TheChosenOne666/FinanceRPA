package com.finrpa.agent.constant;

/**
 * Agent 模块常量
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
public interface AgentConstant {

    /** 请求属性 key：JwtAuthenticationFilter 解析 token 后将 userId 存入 request attribute，供 Controller 读取 */
    String USER_ID_REQUEST_ATTR = "agent.userId";
}
