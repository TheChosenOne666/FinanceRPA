package com.finrpa.tenant.handler;

import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.finrpa.tenant.constant.TenantConstant;
import com.finrpa.tenant.context.TenantContext;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import org.springframework.stereotype.Component;

/**
 * MyBatis-Plus 租户行处理器实现
 *
 * <p>从 {@link TenantContext} 读取当前组织 ID，自动在 SQL 中追加 {@code WHERE org_id = ?} 过滤条件；
 * 通过 {@link TenantConstant#IGNORED_TABLES} 与 {@code skyvern_*} 前缀匹配排除不参与过滤的表。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Slf4j
@Component
public class TenantLineHandlerImpl implements TenantLineHandler {

    /**
     * 获取当前租户 ID（组织 ID，BIGINT 类型）
     *
     * @return 组织 ID 数值字面值；上下文未设置时返回 0 表示无匹配
     */
    @Override
    public Expression getTenantId() {
        // 1. 从上下文读取组织 ID（雪花算法 ID，字符串形式）
        String orgId = TenantContext.getOrgId();
        if (orgId == null) {
            // 2. 上下文为空时返回不可能匹配的值，确保不会泄露其他租户数据
            log.warn("TenantContext 未设置 orgId，查询将不会匹配任何租户数据");
            return new LongValue(0L);
        }
        return new LongValue(Long.parseLong(orgId));
    }

    /**
     * 获取租户字段名
     *
     * @return 字段名 org_id
     */
    @Override
    public String getTenantIdColumn() {
        return TenantConstant.ORG_ID_COLUMN;
    }

    /**
     * 判断指定表是否不参与租户过滤
     *
     * @param tableName 表名
     * @return true-忽略该表；false-对该表追加过滤条件
     */
    @Override
    public boolean ignoreTable(String tableName) {
        // 1. null 表名保守处理：不忽略（交由后续 SQL 流程判定）
        if (tableName == null) {
            return false;
        }
        // 2. 显式忽略清单
        if (TenantConstant.IGNORED_TABLES.contains(tableName)) {
            return true;
        }
        // 3. Skyvern 核心表（前缀匹配）
        return tableName.startsWith("skyvern_");
    }
}
