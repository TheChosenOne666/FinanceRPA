package com.finrpa.audit.storage;

import com.finrpa.audit.dto.response.ScreenshotUploadVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * MinIO 截图存储服务（M7.2，系统设计 6.4.4 + ADR-004）
 *
 * <p>负责审计截图的上传、预签名 URL 生成与过期清理。
 * Python Executor 不直接写 MinIO，而是通过 Java 内部 API 转发，
 * 由 Java 集中管理凭证、签名与记录。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
public interface MinioStorageService {

    /**
     * 上传截图到 MinIO 并返回预签名 URL
     *
     * <p>对象路径规则（系统设计 6.4.4）：{@code {date}/{task_id}/{step_index}_{phase}.png}
     * Bucket 规则：{@code finrpa-audit-{org_id}}（懒加载，不存在时自动创建）</p>
     *
     * @param file      截图文件（multipart 上传）
     * @param orgId     组织 ID（决定 bucket 名）
     * @param taskId    任务 ID
     * @param stepIndex 步骤序号
     * @param phase     阶段：before / after
     * @return 上传结果（含对象路径与预签名 URL）
     */
    ScreenshotUploadVO uploadScreenshot(MultipartFile file, Long orgId, Long taskId, Integer stepIndex, String phase);

    /**
     * 为已存储的截图生成预签名 URL
     *
     * @param orgId      组织 ID（决定 bucket 名）
     * @param objectPath 对象路径
     * @return 预签名 URL（有效期由 minio.presign-expiry-hours 配置，默认 1 小时）
     */
    String presignUrl(Long orgId, String objectPath);

    /**
     * 清理超过保留期的截图对象
     *
     * <p>由定时任务 {@code AuditScreenshotCleanupScheduler} 每日凌晨调用，
     * 扫描所有 bucket 并删除超过 {@code minio.retention-days}（默认 90 天）的对象。</p>
     *
     * @return 已删除的对象数量
     */
    int deleteExpiredObjects();
}
