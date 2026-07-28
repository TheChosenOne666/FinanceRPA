package com.finrpa.tenant.handler;

import com.finrpa.tenant.constant.TenantConstant;
import com.finrpa.tenant.context.TenantContext;
import net.sf.jsqlparser.expression.StringValue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TenantLineHandlerImpl 单元测试
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
class TenantLineHandlerImplTest {

    /** 被测对象 */
    private final TenantLineHandlerImpl handler = new TenantLineHandlerImpl();

    @AfterEach
    void tearDown() {
        // 清理 TenantContext，避免线程污染
        TenantContext.clear();
    }

    @Test
    @DisplayName("getTenantId - 上下文有 orgId 时返回该值")
    void getTenantId_WithOrgId_ReturnsStringValue() {
        // 1. 设置上下文
        TenantContext.setOrgId("org-001");

        // 2. 获取租户 ID
        var expression = handler.getTenantId();

        // 3. 验证为 StringValue 且值正确
        assertThat(expression).isInstanceOf(StringValue.class);
        assertThat(((StringValue) expression).getValue()).isEqualTo("org-001");
    }

    @Test
    @DisplayName("getTenantId - 上下文无 orgId 时返回不可能匹配的值 0")
    void getTenantId_WithoutOrgId_ReturnsFallbackValue() {
        // 未设置上下文时获取
        var expression = handler.getTenantId();

        // 验证返回 "0"
        assertThat(expression).isInstanceOf(StringValue.class);
        assertThat(((StringValue) expression).getValue()).isEqualTo("0");
    }

    @Test
    @DisplayName("getTenantIdColumn - 返回 org_id")
    void getTenantIdColumn_ReturnsOrgId() {
        assertThat(handler.getTenantIdColumn()).isEqualTo(TenantConstant.ORG_ID_COLUMN);
        assertThat(handler.getTenantIdColumn()).isEqualTo("org_id");
    }

    @Test
    @DisplayName("ignoreTable - 组织表本身忽略")
    void ignoreTable_OrganizationTable_ReturnsTrue() {
        assertThat(handler.ignoreTable("enterprise_organization")).isTrue();
    }

    @Test
    @DisplayName("ignoreTable - 关联表/字典表忽略")
    void ignoreTable_DictionaryTables_ReturnsTrue() {
        assertThat(handler.ignoreTable("sys_user_role")).isTrue();
        assertThat(handler.ignoreTable("sys_role_permission")).isTrue();
        assertThat(handler.ignoreTable("sys_permission")).isTrue();
        assertThat(handler.ignoreTable("sys_dictionary")).isTrue();
        assertThat(handler.ignoreTable("sys_config")).isTrue();
        assertThat(handler.ignoreTable("sys_audit_log")).isTrue();
    }

    @Test
    @DisplayName("ignoreTable - sys_user / sys_role 忽略（org_id 仅作归属，登录场景需查 sys_user）")
    void ignoreTable_UserAndRoleTables_ReturnsTrue() {
        assertThat(handler.ignoreTable("sys_user")).isTrue();
        assertThat(handler.ignoreTable("sys_role")).isTrue();
    }

    @Test
    @DisplayName("ignoreTable - Skyvern 核心表忽略（前缀匹配）")
    void ignoreTable_SkyvernPrefix_ReturnsTrue() {
        assertThat(handler.ignoreTable("skyvern_tasks")).isTrue();
        assertThat(handler.ignoreTable("skyvern_workflows")).isTrue();
        assertThat(handler.ignoreTable("skyvern_artifacts")).isTrue();
    }

    @Test
    @DisplayName("ignoreTable - 业务表不忽略（参与租户过滤）")
    void ignoreTable_BusinessTables_ReturnsFalse() {
        assertThat(handler.ignoreTable("enterprise_department")).isFalse();
        assertThat(handler.ignoreTable("enterprise_business_line")).isFalse();
    }

    @Test
    @DisplayName("ignoreTable - null 表名不忽略（保守处理）")
    void ignoreTable_NullTable_ReturnsFalse() {
        assertThat(handler.ignoreTable(null)).isFalse();
    }
}
