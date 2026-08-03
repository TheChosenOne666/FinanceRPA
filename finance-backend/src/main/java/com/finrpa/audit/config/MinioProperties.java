package com.finrpa.audit.config;

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
}
