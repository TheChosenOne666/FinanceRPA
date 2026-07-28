package com.finrpa.tenant.context;

/**
 * 租户上下文，基于 ThreadLocal 持有当前请求的组织 ID
 *
 * <p>由 {@link com.finrpa.tenant.interceptor.TenantInterceptor} 在请求开始时设置，
 * 请求结束时清理；MyBatis-Plus TenantLineHandler 通过 {@link #getOrgId()} 读取并自动追加 SQL 过滤条件。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
public class TenantContext {

    /** 当前线程的组织 ID（未设置时为 null，表示不参与租户过滤） */
    private static final ThreadLocal<String> CURRENT_ORG_ID = new ThreadLocal<>();

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
     * 清理当前线程的组织 ID，防止内存泄漏与线程复用污染
     */
    public static void clear() {
        CURRENT_ORG_ID.remove();
    }
}
