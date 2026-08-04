package com.finrpa.system.dto.response;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.sql.Timestamp;
import java.util.List;

/**
 * 系统健康检查视图对象（P2 OPS-1）
 *
 * <p>由 {@code com.finrpa.system.controller.SystemHealthController#check} 返回，
 * 用于设置页「安全策略 · 系统健康」一键检测展示。</p>
 *
 * <p>聚合四类组件检查结果：
 * <ul>
 *   <li>数据库（PostgreSQL，MyBatis-Plus SELECT 1）</li>
 *   <li>缓存（Redis，Redisson ping）</li>
 *   <li>Python AI 服务（HTTP Interface GET /api/v1/ai/skills）</li>
 *   <li>对象存储（MinIO，bucket 列举）</li>
 * </ul>
 * </p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
public class SystemHealthVO implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 整体状态：UP / DEGRADED / DOWN */
    private String overallStatus;

    /** 检查时间戳 */
    private Timestamp checkedAt;

    /** 检查耗时（毫秒） */
    private Long durationMs;

    /** 各组件检查明细 */
    private List<ComponentHealth> components;

    /**
     * 单个组件健康状态
     */
    @Data
    public static class ComponentHealth implements Serializable {

        /** 序列化版本号 */
        @Serial
        private static final long serialVersionUID = 1L;

        /** 组件名：database / redis / ai_service / minio */
        private String name;

        /** 组件展示名：PostgreSQL / Redis / Python AI / MinIO */
        private String displayName;

        /** 状态：UP / DOWN */
        private String status;

        /** 响应时延（毫秒，DOWN 时为 null） */
        private Long latencyMs;

        /** 错误信息（UP 时为 null） */
        private String errorMessage;

        /** 关键信息（如版本号、连接池信息，可选） */
        private String detail;
    }
}
