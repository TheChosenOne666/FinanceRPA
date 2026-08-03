package com.finrpa.audit.storage.impl;

import com.finrpa.audit.config.MinioProperties;
import com.finrpa.audit.constant.AuditConstant;
import com.finrpa.audit.dto.response.ScreenshotUploadVO;
import com.finrpa.audit.storage.MinioStorageService;
import com.finrpa.common.exception.BusinessException;
import com.finrpa.common.exception.ThrowUtils;
import com.finrpa.common.response.ErrorCode;
import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.ListObjectsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.Result;
import io.minio.http.Method;
import io.minio.messages.Item;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

/**
 * MinIO 截图存储服务实现（M7.2，系统设计 6.4.4 + ADR-004）
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Slf4j
@Service
public class MinioStorageServiceImpl implements MinioStorageService {

    /** 日期格式（用于对象路径第一段） */
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** MinIO 客户端 */
    @Resource
    private MinioClient minioClient;

    /** MinIO 配置 */
    @Resource
    private MinioProperties minioProperties;

    // region 上传截图

    /**
     * 上传截图到 MinIO 并返回预签名 URL
     *
     * @param file      截图文件
     * @param orgId     组织 ID
     * @param taskId    任务 ID
     * @param stepIndex 步骤序号
     * @param phase     阶段：before / after
     * @return 上传结果
     */
    @Override
    public ScreenshotUploadVO uploadScreenshot(MultipartFile file, Long orgId, Long taskId, Integer stepIndex, String phase) {
        // 1. 参数校验
        ThrowUtils.throwIf(file == null || file.isEmpty(), ErrorCode.PARAMS_ERROR, "截图文件不能为空");
        ThrowUtils.throwIf(orgId == null, ErrorCode.PARAMS_ERROR, "组织 ID 不能为空");
        ThrowUtils.throwIf(taskId == null, ErrorCode.PARAMS_ERROR, "任务 ID 不能为空");
        ThrowUtils.throwIf(stepIndex == null || stepIndex < 0, ErrorCode.PARAMS_ERROR, "步骤序号不能为空且需非负");
        ThrowUtils.throwIf(!AuditConstant.SCREENSHOT_PHASE_BEFORE.equals(phase)
                        && !AuditConstant.SCREENSHOT_PHASE_AFTER.equals(phase),
                ErrorCode.PARAMS_ERROR, "阶段必须为 before 或 after");

        // 2. 构建 bucket 名与对象路径
        String bucketName = resolveBucketName(orgId);
        String objectPath = resolveObjectPath(taskId, stepIndex, phase);

        // 3. 确保 bucket 存在（懒加载）
        ensureBucket(bucketName);

        // 4. 上传对象
        String contentType = file.getContentType();
        if (contentType == null || contentType.isBlank()) {
            contentType = "image/png";
        }
        try (InputStream stream = file.getInputStream()) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectPath)
                    .stream(stream, file.getSize(), -1)
                    .contentType(contentType)
                    .build());
        } catch (Exception e) {
            log.error("[MinioStorage] 截图上传失败: bucket={}, object={}, error={}",
                    bucketName, objectPath, e.getMessage(), e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "截图上传 MinIO 失败: " + e.getMessage());
        }

        // 5. 生成预签名 URL
        String presignUrl = presignUrl(orgId, objectPath);

        log.info("[MinioStorage] 截图上传成功: bucket={}, object={}, size={}bytes",
                bucketName, objectPath, file.getSize());

        ScreenshotUploadVO vo = new ScreenshotUploadVO();
        vo.setObjectPath(objectPath);
        vo.setPresignUrl(presignUrl);
        return vo;
    }

    // endregion

    // region 预签名 URL

    /**
     * 为已存储的截图生成预签名 URL
     *
     * @param orgId      组织 ID
     * @param objectPath 对象路径
     * @return 预签名 URL
     */
    @Override
    public String presignUrl(Long orgId, String objectPath) {
        ThrowUtils.throwIf(orgId == null, ErrorCode.PARAMS_ERROR, "组织 ID 不能为空");
        ThrowUtils.throwIf(objectPath == null || objectPath.isBlank(), ErrorCode.PARAMS_ERROR, "对象路径不能为空");

        String bucketName = resolveBucketName(orgId);
        int expiryHours = minioProperties.getPresignExpiryHours();
        try {
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucketName)
                    .object(objectPath)
                    .expiry(expiryHours, TimeUnit.HOURS)
                    .build());
        } catch (Exception e) {
            log.error("[MinioStorage] 预签名 URL 生成失败: bucket={}, object={}, error={}",
                    bucketName, objectPath, e.getMessage(), e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "预签名 URL 生成失败: " + e.getMessage());
        }
    }

    // endregion

    // region 清理过期对象

    /**
     * 清理超过保留期的截图对象
     *
     * <p>遍历所有 bucket（通过 listBuckets），删除 lastModified 超过 retention-days 的对象。
     * 实际生产环境对象量大时建议改用 MinIO 生命周期规则，此处提供应用层兜底清理。</p>
     *
     * @return 已删除的对象数量
     */
    @Override
    public int deleteExpiredObjects() {
        int retentionDays = minioProperties.getRetentionDays();
        // 1. 计算过期时间线（对象 lastModified 早于此时间则需删除）
        long cutoffMillis = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(retentionDays);

        int deletedCount = 0;
        try {
            // 2. 遍历所有 bucket
            for (io.minio.messages.Bucket bucket : minioClient.listBuckets()) {
                String bucketName = bucket.name();
                // 3. 仅处理审计 bucket（前缀匹配）
                if (!bucketName.startsWith(minioProperties.getBucketPrefix())) {
                    continue;
                }
                deletedCount += cleanupBucket(bucketName, cutoffMillis);
            }
        } catch (Exception e) {
            log.error("[MinioStorage] 清理过期截图异常: error={}", e.getMessage(), e);
        }

        if (deletedCount > 0) {
            log.info("[MinioStorage] 过期截图清理完成: deleted={}, retentionDays={}", deletedCount, retentionDays);
        }
        return deletedCount;
    }

    /**
     * 清理单个 bucket 中的过期对象
     *
     * @param bucketName  bucket 名
     * @param cutoffMillis 过期时间线（毫秒时间戳）
     * @return 已删除的对象数量
     */
    private int cleanupBucket(String bucketName, long cutoffMillis) {
        int deleted = 0;
        Iterable<Result<Item>> results = minioClient.listObjects(ListObjectsArgs.builder()
                .bucket(bucketName)
                .recursive(true)
                .build());

        for (Result<Item> result : results) {
            try {
                Item item = result.get();
                // 1. 判断是否过期（lastModified 转毫秒时间戳）
                if (item.lastModified().toInstant().toEpochMilli() < cutoffMillis) {
                    // 2. 删除对象
                    minioClient.removeObject(RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(item.objectName())
                            .build());
                    deleted++;
                    log.debug("[MinioStorage] 删除过期截图: bucket={}, object={}, lastModified={}",
                            bucketName, item.objectName(), item.lastModified());
                }
            } catch (Exception e) {
                log.warn("[MinioStorage] 删除对象失败: bucket={}, object={}, error={}",
                        bucketName, itemObjectNameSafe(result, e), e.getMessage());
            }
        }
        return deleted;
    }

    // endregion

    // region 私有方法

    /**
     * 解析 bucket 名：{bucketPrefix}{orgId}
     *
     * @param orgId 组织 ID
     * @return bucket 名
     */
    private String resolveBucketName(Long orgId) {
        return minioProperties.getBucketPrefix() + orgId;
    }

    /**
     * 解析对象路径：{date}/{task_id}/{step_index}_{phase}.png
     *
     * @param taskId    任务 ID
     * @param stepIndex 步骤序号
     * @param phase     阶段
     * @return 对象路径
     */
    private String resolveObjectPath(Long taskId, Integer stepIndex, String phase) {
        String date = LocalDate.now().format(DATE_FORMATTER);
        return date + AuditConstant.SCREENSHOT_PATH_SEPARATOR
                + taskId + AuditConstant.SCREENSHOT_PATH_SEPARATOR
                + stepIndex + "_" + phase + AuditConstant.SCREENSHOT_FILE_EXTENSION;
    }

    /**
     * 确保 bucket 存在，不存在则创建（懒加载）
     *
     * @param bucketName bucket 名
     */
    private void ensureBucket(String bucketName) {
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
                log.info("[MinioStorage] Bucket 自动创建: {}", bucketName);
            }
        } catch (Exception e) {
            log.error("[MinioStorage] Bucket 检查/创建失败: bucket={}, error={}",
                    bucketName, e.getMessage(), e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "Bucket 初始化失败: " + e.getMessage());
        }
    }

    /**
     * 安全获取对象名（清理异常时 result.get() 可能再次抛异常）
     *
     * @param result MinIO 结果
     * @param e      原始异常
     * @return 对象名或 unknown
     */
    private String itemObjectNameSafe(Result<Item> result, Exception e) {
        try {
            return result.get().objectName();
        } catch (Exception ex) {
            return "unknown";
        }
    }

    // endregion
}
