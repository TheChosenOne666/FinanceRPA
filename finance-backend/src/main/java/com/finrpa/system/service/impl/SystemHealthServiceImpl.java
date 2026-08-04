package com.finrpa.system.service.impl;

import com.finrpa.ai.client.AiServiceClient;
import com.finrpa.audit.config.MinioProperties;
import com.finrpa.system.dto.response.SystemHealthVO;
import com.finrpa.system.dto.response.SystemHealthVO.ComponentHealth;
import com.finrpa.system.mapper.SystemHealthMapper;
import com.finrpa.system.service.SystemHealthService;
import io.minio.MinioClient;
import io.minio.messages.Bucket;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * 系统健康检查服务实现（P2 OPS-1）
 *
 * <p>逐个检查 DB / Redis / Python AI / MinIO，单组件失败不影响其他组件。
 * 所有检查结果聚合到 {@link SystemHealthVO} 返回，不抛异常。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Slf4j
@Service
public class SystemHealthServiceImpl implements SystemHealthService {

    /** 组件状态 UP */
    private static final String STATUS_UP = "UP";
    /** 组件状态 DOWN */
    private static final String STATUS_DOWN = "DOWN";
    /** 整体状态 - 全部正常 */
    private static final String OVERALL_UP = "UP";
    /** 整体状态 - 部分降级 */
    private static final String OVERALL_DEGRADED = "DEGRADED";
    /** 整体状态 - 全部不可用 */
    private static final String OVERALL_DOWN = "DOWN";

    /** 系统健康检查 Mapper（SELECT 1） */
    @Resource
    private SystemHealthMapper systemHealthMapper;

    /** Redisson 客户端（Redis ping） */
    @Resource
    private RedissonClient redissonClient;

    /** Python AI 服务 HTTP Interface 客户端 */
    @Resource
    private AiServiceClient aiServiceClient;

    /** MinIO 客户端（bucket 列举） */
    @Resource
    private MinioClient minioClient;

    /** MinIO 配置（用于展示 endpoint） */
    @Resource
    private MinioProperties minioProperties;

    // region 健康检查入口

    /**
     * 执行全量健康检查
     *
     * @return 健康检查结果
     */
    @Override
    public SystemHealthVO check() {
        long start = System.currentTimeMillis();
        List<ComponentHealth> components = new ArrayList<>();

        // 1. 检查 DB
        components.add(checkDatabase());
        // 2. 检查 Redis
        components.add(checkRedis());
        // 3. 检查 Python AI 服务
        components.add(checkAiService());
        // 4. 检查 MinIO
        components.add(checkMinio());

        // 5. 计算整体状态
        String overallStatus = computeOverallStatus(components);

        // 6. 构建返回 VO
        SystemHealthVO vo = new SystemHealthVO();
        vo.setOverallStatus(overallStatus);
        vo.setCheckedAt(new Timestamp(System.currentTimeMillis()));
        vo.setDurationMs(System.currentTimeMillis() - start);
        vo.setComponents(components);
        return vo;
    }

    // endregion

    // region 单组件检查

    /**
     * 检查数据库连通性（SELECT 1）
     *
     * @return 组件健康状态
     */
    private ComponentHealth checkDatabase() {
        ComponentHealth component = newComponent("database", "PostgreSQL");
        long start = System.currentTimeMillis();
        try {
            Integer result = systemHealthMapper.ping();
            component.setStatus(STATUS_UP);
            component.setLatencyMs(System.currentTimeMillis() - start);
            component.setDetail("SELECT 1 → " + result);
        } catch (Exception e) {
            log.warn("数据库健康检查失败", e);
            component.setStatus(STATUS_DOWN);
            component.setLatencyMs(System.currentTimeMillis() - start);
            component.setErrorMessage(e.getMessage());
        }
        return component;
    }

    /**
     * 检查 Redis 连通性（Redisson getKeys().count() 轻量调用）
     *
     * <p>不使用 {@code getNodesGroup().pingAll()}，因为 Cluster 模式下需要 NodesGroup，
     * Single 模式下该方法行为不一致；{@code getKeys().count()} 在两种模式下都能工作，
     * 仅做 key 数量统计，不影响业务数据。</p>
     *
     * @return 组件健康状态
     */
    private ComponentHealth checkRedis() {
        ComponentHealth component = newComponent("redis", "Redis");
        long start = System.currentTimeMillis();
        try {
            long keyCount = redissonClient.getKeys().count();
            component.setStatus(STATUS_UP);
            component.setLatencyMs(System.currentTimeMillis() - start);
            component.setDetail("keys=" + keyCount);
        } catch (Exception e) {
            log.warn("Redis 健康检查失败", e);
            component.setStatus(STATUS_DOWN);
            component.setLatencyMs(System.currentTimeMillis() - start);
            component.setErrorMessage(e.getMessage());
        }
        return component;
    }

    /**
     * 检查 Python AI 服务连通性（HTTP Interface GET /api/v1/ai/skills）
     *
     * <p>GET /skills 是轻量接口，仅返回 Skill 元数据列表，不触发任务执行。</p>
     *
     * @return 组件健康状态
     */
    private ComponentHealth checkAiService() {
        ComponentHealth component = newComponent("ai_service", "Python AI");
        long start = System.currentTimeMillis();
        try {
            aiServiceClient.getSkills();
            component.setStatus(STATUS_UP);
            component.setLatencyMs(System.currentTimeMillis() - start);
            component.setDetail("GET /api/v1/ai/skills OK");
        } catch (Exception e) {
            log.warn("Python AI 服务健康检查失败", e);
            component.setStatus(STATUS_DOWN);
            component.setLatencyMs(System.currentTimeMillis() - start);
            component.setErrorMessage(e.getMessage());
        }
        return component;
    }

    /**
     * 检查 MinIO 连通性（列举 buckets）
     *
     * @return 组件健康状态
     */
    private ComponentHealth checkMinio() {
        ComponentHealth component = newComponent("minio", "MinIO");
        long start = System.currentTimeMillis();
        try {
            List<Bucket> buckets = minioClient.listBuckets();
            component.setStatus(STATUS_UP);
            component.setLatencyMs(System.currentTimeMillis() - start);
            component.setDetail("buckets=" + buckets.size() + ", endpoint=" + minioProperties.getEndpoint());
        } catch (Exception e) {
            log.warn("MinIO 健康检查失败", e);
            component.setStatus(STATUS_DOWN);
            component.setLatencyMs(System.currentTimeMillis() - start);
            component.setErrorMessage(e.getMessage());
        }
        return component;
    }

    // endregion

    // region 私有工具

    /**
     * 构建初始 ComponentHealth
     *
     * @param name        组件名
     * @param displayName 展示名
     * @return 初始组件健康状态
     */
    private ComponentHealth newComponent(String name, String displayName) {
        ComponentHealth component = new ComponentHealth();
        component.setName(name);
        component.setDisplayName(displayName);
        return component;
    }

    /**
     * 根据各组件状态计算整体状态
     *
     * <p>规则：全 UP → UP；部分 DOWN → DEGRADED；全 DOWN → DOWN</p>
     *
     * @param components 各组件检查结果
     * @return 整体状态
     */
    private String computeOverallStatus(List<ComponentHealth> components) {
        long upCount = components.stream()
                .filter(c -> STATUS_UP.equals(c.getStatus()))
                .count();
        if (upCount == components.size()) {
            return OVERALL_UP;
        } else if (upCount == 0) {
            return OVERALL_DOWN;
        } else {
            return OVERALL_DEGRADED;
        }
    }

    // endregion
}
