package com.finrpa.tenant.context;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TenantContext 单元测试
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
class TenantContextTest {

    @AfterEach
    void tearDown() {
        // 每个测试后清理，避免线程复用污染
        TenantContext.clear();
    }

    @Test
    @DisplayName("设置和获取组织 ID - 成功")
    void setAndGetOrgId_Success() {
        // 1. 设置组织 ID
        TenantContext.setOrgId("org-001");

        // 2. 验证获取值一致
        assertThat(TenantContext.getOrgId()).isEqualTo("org-001");
    }

    @Test
    @DisplayName("未设置时获取组织 ID - 返回 null")
    void getOrgId_NotSet_ReturnsNull() {
        // 未设置时返回 null
        assertThat(TenantContext.getOrgId()).isNull();
    }

    @Test
    @DisplayName("清理后获取组织 ID - 返回 null")
    void clear_AfterSet_ReturnsNull() {
        // 1. 设置组织 ID
        TenantContext.setOrgId("org-002");

        // 2. 清理
        TenantContext.clear();

        // 3. 验证已清理
        assertThat(TenantContext.getOrgId()).isNull();
    }

    @Test
    @DisplayName("覆盖设置 - 后设置的值生效")
    void setOrgId_OverrideValue() {
        // 1. 第一次设置
        TenantContext.setOrgId("org-A");

        // 2. 第二次覆盖
        TenantContext.setOrgId("org-B");

        // 3. 验证最新值生效
        assertThat(TenantContext.getOrgId()).isEqualTo("org-B");
    }
}
