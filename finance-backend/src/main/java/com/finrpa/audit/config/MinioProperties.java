package com.finrpa.audit.config;

import com.finrpa.system.service.SystemConfigService;
import io.minio.MinioClient;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MinIO 对象存储配置（M7.2）
 *
 * <p>绑定 application.yml 中 {@code minio.*} 配置项，并构建 {@link MinioClient} Bean。
 * 审计截图统一走 Java 转发 MinIO（ADR-004），凭证集中管理于此。</p>
 *
 * <p>P3 INT-3：增加 {@link #refreshFromConfig(SystemConfigService)} 方法，从 sys_config 表
 * 读取最新配置并更新字段值。高频字段（presignExpiryHours / retentionDays / bucketPrefix）
 * 刷新后通过 getter 立即热生效；连接参数（endpoint / accessKey / secretKey）刷新后因
 * MinioClient 为启动时构建的单例，需重启或调重建接口生效。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "minio")
public class MinioProperties {

    /** MinIO 服务地址（含协议与端口） */
    private String endpoint;

    /** 访问密钥 */
    private String accessKey;

    /** 秘密密钥 */
    private String secretKey;

    /** Bucket 前缀，完整 bucket 名为 {prefix}{org_id} */
    private String bucketPrefix;

    /** 预签名 URL 有效期（小时） */
    private int presignExpiryHours;

    /** 截图保留期（天） */
    private int retentionDays;

    /**
     * 构建 MinIO 客户端
     *
     * @return MinioClient 实例
     */
    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }

    /**
     * 从系统配置服务刷新字段值（P3 INT-3）
     *
     * <p>DB 配置缺失时保留 yml 绑定的当前值。高频字段（presignExpiryHours / retentionDays /
     * bucketPrefix）刷新后立即热生效；连接参数（endpoint / accessKey / secretKey）刷新后
     * 需重启或调 MinioClient 重建接口生效。</p>
     *
     * @param configService 系统配置服务
     */
    public void refreshFromConfig(SystemConfigService configService) {
        // 1. 连接参数（改动需重启重建 MinioClient）
        String dbEndpoint = configService.getString("minio.endpoint", null);
        if (dbEndpoint != null) {
            this.endpoint = dbEndpoint;
        }
        String dbAccessKey = configService.getString("minio.access_key", null);
        if (dbAccessKey != null) {
            this.accessKey = dbAccessKey;
        }
        String dbSecretKey = configService.getString("minio.secret_key", null);
        if (dbSecretKey != null) {
            this.secretKey = dbSecretKey;
        }
        // 2. 高频参数（刷新后立即热生效）
        String dbBucketPrefix = configService.getString("minio.bucket_prefix", null);
        if (dbBucketPrefix != null) {
            this.bucketPrefix = dbBucketPrefix;
        }
        Integer dbPresignExpiryHours = configService.getInteger("minio.presign_expiry_hours", null);
        if (dbPresignExpiryHours != null) {
            this.presignExpiryHours = dbPresignExpiryHours;
        }
        Integer dbRetentionDays = configService.getInteger("minio.retention_days", null);
        if (dbRetentionDays != null) {
            this.retentionDays = dbRetentionDays;
        }
    }
}
