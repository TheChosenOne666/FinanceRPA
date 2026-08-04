package com.finrpa.system.service.impl;

import com.finrpa.ai.client.AiServiceClient;
import com.finrpa.audit.config.MinioProperties;
import com.finrpa.system.dto.response.SystemHealthVO;
import com.finrpa.system.dto.response.SystemHealthVO.ComponentHealth;
import com.finrpa.system.mapper.SystemHealthMapper;
import io.minio.MinioClient;
import io.minio.messages.Bucket;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RKeys;
import org.redisson.api.RedissonClient;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * 系统健康检查服务实现单元测试（P2 OPS-1）
 *
 * <p>覆盖四类组件（DB / Redis / Python AI / MinIO）的 UP / DOWN 分支，
 * 以及整体状态（UP / DEGRADED / DOWN）计算逻辑。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@ExtendWith(MockitoExtension.class)
class SystemHealthServiceImplTest {

    @Mock
    private SystemHealthMapper systemHealthMapper;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RKeys rKeys;

    @Mock
    private AiServiceClient aiServiceClient;

    @Mock
    private MinioClient minioClient;

    @Mock
    private MinioProperties minioProperties;

    @InjectMocks
    private SystemHealthServiceImpl systemHealthService;

    // region DB 检查

    @Test
    @DisplayName("checkDatabase - SELECT 1 返回 1 → UP")
    void checkDatabase_Up() {
        when(systemHealthMapper.ping()).thenReturn(1);
        when(redissonClient.getKeys()).thenReturn(rKeys);
        when(rKeys.count()).thenReturn(5L);
        when(aiServiceClient.getSkills()).thenReturn(Collections.emptyList());
        when(minioProperties.getEndpoint()).thenReturn("http://localhost:9000");

        SystemHealthVO vo = systemHealthService.check();

        ComponentHealth db = findComponent(vo, "database");
        assertThat(db.getStatus()).isEqualTo("UP");
        assertThat(db.getLatencyMs()).isNotNull();
        assertThat(db.getDetail()).contains("SELECT 1");
        assertThat(db.getErrorMessage()).isNull();
    }

    @Test
    @DisplayName("checkDatabase - 抛异常 → DOWN")
    void checkDatabase_Down() {
        when(systemHealthMapper.ping()).thenThrow(new RuntimeException("connection refused"));
        when(redissonClient.getKeys()).thenReturn(rKeys);
        when(rKeys.count()).thenReturn(5L);
        when(aiServiceClient.getSkills()).thenReturn(Collections.emptyList());
        when(minioProperties.getEndpoint()).thenReturn("http://localhost:9000");

        SystemHealthVO vo = systemHealthService.check();

        ComponentHealth db = findComponent(vo, "database");
        assertThat(db.getStatus()).isEqualTo("DOWN");
        assertThat(db.getErrorMessage()).contains("connection refused");
    }

    // endregion

    // region Redis 检查

    @Test
    @DisplayName("checkRedis - getKeys().count() 返回 → UP")
    void checkRedis_Up() {
        when(systemHealthMapper.ping()).thenReturn(1);
        when(redissonClient.getKeys()).thenReturn(rKeys);
        when(rKeys.count()).thenReturn(42L);
        when(aiServiceClient.getSkills()).thenReturn(Collections.emptyList());
        when(minioProperties.getEndpoint()).thenReturn("http://localhost:9000");

        SystemHealthVO vo = systemHealthService.check();

        ComponentHealth redis = findComponent(vo, "redis");
        assertThat(redis.getStatus()).isEqualTo("UP");
        assertThat(redis.getDetail()).contains("keys=42");
    }

    @Test
    @DisplayName("checkRedis - 抛异常 → DOWN")
    void checkRedis_Down() {
        when(systemHealthMapper.ping()).thenReturn(1);
        when(redissonClient.getKeys()).thenReturn(rKeys);
        when(rKeys.count()).thenThrow(new RuntimeException("Redis unreachable"));
        when(aiServiceClient.getSkills()).thenReturn(Collections.emptyList());
        when(minioProperties.getEndpoint()).thenReturn("http://localhost:9000");

        SystemHealthVO vo = systemHealthService.check();

        ComponentHealth redis = findComponent(vo, "redis");
        assertThat(redis.getStatus()).isEqualTo("DOWN");
        assertThat(redis.getErrorMessage()).contains("Redis unreachable");
    }

    // endregion

    // region Python AI 检查

    @Test
    @DisplayName("checkAiService - getSkills() 返回 → UP")
    void checkAiService_Up() {
        when(systemHealthMapper.ping()).thenReturn(1);
        when(redissonClient.getKeys()).thenReturn(rKeys);
        when(rKeys.count()).thenReturn(5L);
        when(aiServiceClient.getSkills()).thenReturn(Collections.emptyList());
        when(minioProperties.getEndpoint()).thenReturn("http://localhost:9000");

        SystemHealthVO vo = systemHealthService.check();

        ComponentHealth ai = findComponent(vo, "ai_service");
        assertThat(ai.getStatus()).isEqualTo("UP");
        assertThat(ai.getDetail()).contains("/api/v1/ai/skills");
    }

    @Test
    @DisplayName("checkAiService - 抛异常 → DOWN")
    void checkAiService_Down() {
        when(systemHealthMapper.ping()).thenReturn(1);
        when(redissonClient.getKeys()).thenReturn(rKeys);
        when(rKeys.count()).thenReturn(5L);
        when(aiServiceClient.getSkills()).thenThrow(new RuntimeException("503 Service Unavailable"));
        when(minioProperties.getEndpoint()).thenReturn("http://localhost:9000");

        SystemHealthVO vo = systemHealthService.check();

        ComponentHealth ai = findComponent(vo, "ai_service");
        assertThat(ai.getStatus()).isEqualTo("DOWN");
        assertThat(ai.getErrorMessage()).contains("503 Service Unavailable");
    }

    // endregion

    // region MinIO 检查

    @Test
    @DisplayName("checkMinio - listBuckets() 返回 → UP")
    void checkMinio_Up() throws Exception {
        when(systemHealthMapper.ping()).thenReturn(1);
        when(redissonClient.getKeys()).thenReturn(rKeys);
        when(rKeys.count()).thenReturn(5L);
        when(aiServiceClient.getSkills()).thenReturn(Collections.emptyList());
        when(minioProperties.getEndpoint()).thenReturn("http://localhost:9000");
        // MinIO 客户端 mock：listBuckets 返回空列表
        when(minioClient.listBuckets()).thenReturn(Collections.<Bucket>emptyList());

        SystemHealthVO vo = systemHealthService.check();

        ComponentHealth minio = findComponent(vo, "minio");
        assertThat(minio.getStatus()).isEqualTo("UP");
        assertThat(minio.getDetail()).contains("buckets=0");
        assertThat(minio.getDetail()).contains("http://localhost:9000");
    }

    @Test
    @DisplayName("checkMinio - 抛异常 → DOWN")
    void checkMinio_Down() throws Exception {
        when(systemHealthMapper.ping()).thenReturn(1);
        when(redissonClient.getKeys()).thenReturn(rKeys);
        when(rKeys.count()).thenReturn(5L);
        when(aiServiceClient.getSkills()).thenReturn(Collections.emptyList());
        when(minioClient.listBuckets())
                .thenThrow(new RuntimeException("MinIO connection refused"));

        SystemHealthVO vo = systemHealthService.check();

        ComponentHealth minio = findComponent(vo, "minio");
        assertThat(minio.getStatus()).isEqualTo("DOWN");
        assertThat(minio.getErrorMessage()).contains("MinIO connection refused");
    }

    // endregion

    // region 整体状态计算

    @Test
    @DisplayName("computeOverallStatus - 全部 UP → UP")
    void checkOverallStatus_AllUp() throws Exception {
        when(systemHealthMapper.ping()).thenReturn(1);
        when(redissonClient.getKeys()).thenReturn(rKeys);
        when(rKeys.count()).thenReturn(5L);
        when(aiServiceClient.getSkills()).thenReturn(Collections.emptyList());
        when(minioProperties.getEndpoint()).thenReturn("http://localhost:9000");
        when(minioClient.listBuckets()).thenReturn(Collections.<Bucket>emptyList());

        SystemHealthVO vo = systemHealthService.check();

        assertThat(vo.getOverallStatus()).isEqualTo("UP");
        assertThat(vo.getComponents()).hasSize(4);
        assertThat(vo.getCheckedAt()).isNotNull();
        assertThat(vo.getDurationMs()).isGreaterThanOrEqualTo(0L);
    }

    @Test
    @DisplayName("computeOverallStatus - 部分 DOWN → DEGRADED")
    void checkOverallStatus_PartialDown() throws Exception {
        when(systemHealthMapper.ping()).thenReturn(1);
        when(redissonClient.getKeys()).thenReturn(rKeys);
        when(rKeys.count()).thenReturn(5L);
        when(aiServiceClient.getSkills()).thenThrow(new RuntimeException("ai down"));
        when(minioProperties.getEndpoint()).thenReturn("http://localhost:9000");
        when(minioClient.listBuckets()).thenReturn(Collections.<Bucket>emptyList());

        SystemHealthVO vo = systemHealthService.check();

        assertThat(vo.getOverallStatus()).isEqualTo("DEGRADED");
    }

    @Test
    @DisplayName("computeOverallStatus - 全部 DOWN → DOWN")
    void checkOverallStatus_AllDown() throws Exception {
        when(systemHealthMapper.ping()).thenThrow(new RuntimeException("db down"));
        when(redissonClient.getKeys()).thenReturn(rKeys);
        when(rKeys.count()).thenThrow(new RuntimeException("redis down"));
        when(aiServiceClient.getSkills()).thenThrow(new RuntimeException("ai down"));
        when(minioClient.listBuckets())
                .thenThrow(new RuntimeException("minio down"));

        SystemHealthVO vo = systemHealthService.check();

        assertThat(vo.getOverallStatus()).isEqualTo("DOWN");
        assertThat(vo.getComponents()).hasSize(4);
        assertThat(vo.getComponents()).allMatch(c -> "DOWN".equals(c.getStatus()));
    }

    // endregion

    // region 私有工具

    /**
     * 从 VO 中按组件名查找
     */
    private ComponentHealth findComponent(SystemHealthVO vo, String name) {
        return vo.getComponents().stream()
                .filter(c -> name.equals(c.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Component not found: " + name));
    }

    // endregion
}
