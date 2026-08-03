package com.finrpa.tenant.context;

/**
 * 租户上下文，基于 ThreadLocal 持有当前请求的组织 ID 与用户 ID
 *
 * <p>由 {@link com.finrpa.tenant.interceptor.TenantInterceptor} 在请求开始时设置，
 * 请求结束时清理；MyBatis-Plus TenantLineHandler 通过 {@link #getOrgId()} 读取并自动追加 SQL 过滤条件。</p>
 *
 * <p>M7.6 三维度 RBAC 起，新增 {@link #getUserId()} 供 Service 层读取当前用户 ID，
 * 用于按业务线/部门范围过滤数据。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
public class TenantContext {

    /** 当前线程的组织 ID（未设置时为 null，表示不参与租户过滤） */
    private static final ThreadLocal<String> CURRENT_ORG_ID = new ThreadLocal<>();

    /** 当前线程的用户 ID（M7.6 三维度 RBAC：业务线/部门范围过滤使用） */
    private static final ThreadLocal<String> CURRENT_USER_ID = new ThreadLocal<>();

    /**
     * 私有构造，禁止实例化
     */
    private TenantContext() {
    }

    /**
     * 设置当前线程的组织 ID
     *
     * @param orgId 组织 ID
     */
    public static void setOrgId(String orgId) {
        CURRENT_ORG_ID.set(orgId);
    }

    /**
     * 获取当前线程的组织 ID
     *
     * @return 组织 ID；未设置时返回 null
     */
    public static String getOrgId() {
        return CURRENT_ORG_ID.get();
    }

    /**
     * 设置当前线程的用户 ID（M7.6 三维度 RBAC）
     *
     * @param userId 用户 ID
     */
    public static void setUserId(String userId) {
        CURRENT_USER_ID.set(userId);
    }

    /**
     * 获取当前线程的用户 ID（M7.6 三维度 RBAC：业务线/部门范围过滤使用）
     *
     * @return 用户 ID；未设置时返回 null
     */
    public static String getUserId() {
        return CURRENT_USER_ID.get();
    }

    /**
     * 清理当前线程的组织 ID 与用户 ID，防止内存泄漏与线程复用污染
     */
    public static void clear() {
        CURRENT_ORG_ID.remove();
        CURRENT_USER_ID.remove();
    }
}
