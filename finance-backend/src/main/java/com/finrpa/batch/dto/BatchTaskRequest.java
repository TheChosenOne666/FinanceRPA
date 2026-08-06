package com.finrpa.batch.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 批量任务创建请求
 *
 * <p>数据驱动入口：将一批用户数据（来自 CSV/Excel 或外部数据库）映射到同一工作流模板的
 * 参数，逐个触发执行，避免人工重复录入参数。支持两种数据来源：
 * <ul>
 *   <li>{@code rows}：直接传多行参数（前端解析 CSV/粘贴多行后提交）</li>
 *   <li>{@code externalQuery}：指定外部数据源表与字段映射，后端拉取后生成</li>
 * </ul>
 * </p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
public class BatchTaskRequest implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 工作流模板业务 ID（必填） */
    private Long workflowId;

    /**
     * 参数映射：CSV/外部表列名 → 工作流模板 params 的 name
     * （如 {"客户姓名":"customer_name","身份证号":"id_card"}）
     */
    private Map<String, String> columnMapping = new java.util.HashMap<>();

    /** 直接传入的多行参数数据（每行是一个 name→value 的映射） */
    private List<Map<String, Object>> rows;

    /** 外部数据源查询配置（与 rows 二选一） */
    private ExternalQuery externalQuery;

    /** 外部数据源查询配置 */
    @Data
    public static class ExternalQuery implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        /** 目标表名 */
        private String tableName;

        /** 查询条件（WHERE 子句，可选，不含 WHERE 关键字） */
        private String whereClause;

        /** 限制条数（默认 100，最大 1000） */
        private Integer limit = 100;
    }
}
