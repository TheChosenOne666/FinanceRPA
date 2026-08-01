package com.finrpa.tenant.constant;

import java.util.Set;

/**
 * 租户隔离相关常量
 *
 * <p>定义不参与租户过滤的表清单（如组织表本身、Skyvern 核心表、权限关联表等无 org_id 字段的表）。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
public interface TenantConstant {

    /** 租户字段名（统一使用 org_id，对齐 sys_user/sys_role 已有字段） */
    String ORG_ID_COLUMN = "org_id";

    /** 请求属性 key：JwtAuthenticationFilter 解析 token 后将 orgId 存入 request attribute，供 TenantInterceptor 读取 */
    String ORG_ID_REQUEST_ATTR = "tenant.orgId";

    /** 不参与租户过滤的表清单 */
    Set<String> IGNORED_TABLES = Set.of(
            // 组织表本身（无 org_id 字段）
            "enterprise_organization",
            // sys_user / sys_role：org_id 字段用于数据归属展示，不参与自动过滤
            // 原因：登录场景下 TenantContext 尚未建立，自动过滤会导致查不到用户；
            // 用户与角色的跨组织管理由 super_admin / org_admin 显式控制
            "sys_user",
            "sys_role",
            // 用户-角色、角色-权限、权限、字典、配置等关联/字典表（无 org_id 字段）
            "sys_user_role",
            "sys_role_permission",
            "sys_permission",
            "sys_dictionary",
            "sys_config",
            "sys_audit_log",
            // RPA 任务执行/日志/浏览器会话/审批等表暂未带 org_id 字段，先忽略（后续如有需要再加字段）
            "rpa_task_execution",
            "rpa_task_log",
            "rpa_browser_session",
            "rpa_approval",
            // Agent 任务表：有 org_id 字段，但内部回调（Python → Java）无 JWT 上下文，
            // 需绕过自动租户过滤；对外接口在 Service 层手动按 orgId 过滤
            "rpa_agent_task",
            "rpa_agent_subtask",
            "rpa_agent_coordination_state",
            // 审计日志表：有 org_id 字段，但由 Python 回调写入（无 JWT 上下文），
            // org_id 由 Python 端显式传入，需绕过自动租户过滤
            "rpa_audit_log",
            // LLM 调用记录表：有 org_id 字段，但由 Python 回调写入（无 JWT 上下文），
            // org_id 由 Python 端显式传入，需绕过自动租户过滤；对外统计 API 在 Service 层手动按 orgId 过滤
            "rpa_llm_call_log",
            // NEEDS_HUMAN 队列表：有 org_id 字段，但由 Python 回调写入（无 JWT 上下文），
            // org_id 由 Python 端显式传入，需绕过自动租户过滤；对外 API 在 Service 层手动按 orgId 过滤
            "rpa_needs_human_queue",
            // Skill 元数据表：全局共享（无 org_id 字段），所有租户用同一套 Skill 定义
            "rpa_skill_meta",
            // 工作流模板表：全局共享（无 org_id 字段），所有租户用同一套模板
            "rpa_workflow_template",
            // 风险关键词库表：全局共享（无 org_id 字段），所有租户用同一套关键词库
            "rpa_risk_keyword",
            // Skyvern 核心表（Python 侧 Alembic 管理，Java 侧不感知字段）
            "skyvern_*"
    );
}
