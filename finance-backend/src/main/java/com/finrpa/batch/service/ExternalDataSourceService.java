package com.finrpa.batch.service;

import com.finrpa.common.exception.BusinessException;
import com.finrpa.common.response.ErrorCode;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 外部业务系统数据源服务
 *
 * <p>为「数据库/业务系统对接」提供只读连接能力：基于 {@code application.yml} 的
 * {@code external-datasource} 配置动态建立 {@link HikariDataSource}，支持预览表数据，
 * 供批量任务将外部客户清单映射为工作流参数。不引入多数据源框架，仅维护一个按需创建的
 * 外部只读连接池。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Slf4j
@Service
public class ExternalDataSourceService {

    /** 是否启用外部数据源 */
    @Value("${external-datasource.enabled:false}")
    private boolean enabled;

    /** JDBC URL */
    @Value("${external-datasource.url:}")
    private String url;

    /** 驱动类 */
    @Value("${external-datasource.driver-class-name:org.postgresql.Driver}")
    private String driverClassName;

    /** 用户名 */
    @Value("${external-datasource.username:}")
    private String username;

    /** 密码 */
    @Value("${external-datasource.password:}")
    private String password;

    /** 单例连接池（懒加载，首次使用时创建） */
    private volatile HikariDataSource dataSource;

    /**
     * 是否启用外部数据源
     *
     * @return 启用标记
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 获取（懒加载）外部数据源
     *
     * @return 数据源
     */
    private DataSource getDataSource() {
        if (!enabled || url == null || url.isBlank()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "外部数据源未配置或未启用");
        }
        HikariDataSource ds = dataSource;
        if (ds == null) {
            synchronized (this) {
                ds = dataSource;
                if (ds == null) {
                    HikariConfig config = new HikariConfig();
                    config.setJdbcUrl(url);
                    config.setDriverClassName(driverClassName);
                    config.setUsername(username);
                    config.setPassword(password);
                    config.setMaximumPoolSize(5);
                    config.setReadOnly(true);
                    config.setPoolName("external-ds-pool");
                    ds = new HikariDataSource(config);
                    dataSource = ds;
                    log.info("外部数据源连接池已创建: url={}", url);
                }
            }
        }
        return ds;
    }

    /**
     * 预览外部表数据（用于前端字段映射配置）
     *
     * @param tableName   目标表名
     * @param whereClause 可选 WHERE 子句（不含 WHERE 关键字）
     * @param limit       限制条数（1-1000）
     * @return 行数据（LinkedHashMap 保持列顺序）
     */
    public List<Map<String, Object>> previewTable(String tableName, String whereClause, int limit) {
        validateTableName(tableName);
        int safeLimit = Math.max(1, Math.min(limit, 1000));
        StringBuilder sql = new StringBuilder("SELECT * FROM ").append(tableName);
        if (whereClause != null && !whereClause.isBlank()) {
            sql.append(" WHERE ").append(sanitizeClause(whereClause));
        }
        sql.append(" LIMIT ").append(safeLimit);

        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection conn = getDataSource().getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql.toString())) {
            int colCount = rs.getMetaData().getColumnCount();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= colCount; i++) {
                    row.put(rs.getMetaData().getColumnLabel(i), rs.getObject(i));
                }
                rows.add(row);
            }
        } catch (SQLException e) {
            log.error("外部表预览失败: table={}", tableName, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "外部表查询失败: " + e.getMessage());
        }
        return rows;
    }

    /**
     * 校验表名（仅允许字母、数字、下划线、点，禁止 SQL 注入）
     */
    private void validateTableName(String tableName) {
        if (tableName == null || !tableName.matches("^[a-zA-Z_][a-zA-Z0-9_\\.]*$")) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "非法的表名: " + tableName);
        }
    }

    /**
     * 清洗 WHERE 子句：仅允许基础字符，阻断注释与语句分隔符
     */
    private String sanitizeClause(String clause) {
        if (clause.contains("--") || clause.contains(";") || clause.toLowerCase().contains("/*")) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "WHERE 子句包含非法字符");
        }
        return clause;
    }
}
