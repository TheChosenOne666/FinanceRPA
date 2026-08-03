package com.finrpa.audit.storage.impl;

import com.finrpa.audit.config.MinioProperties;
import com.finrpa.audit.dto.response.ScreenshotUploadVO;
import com.finrpa.common.exception.BusinessException;
import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.ListObjectsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.Result;
import io.minio.messages.Bucket;
import io.minio.messages.Item;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * MinIO 截图存储服务实现单元测试（M7.2）
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@ExtendWith(MockitoExtension.class)
class MinioStorageServiceImplTest {

    /** 测试用组织 ID */
    private static final Long TEST_ORG_ID = 2082342545947660289L;

    /** 测试用任务 ID */
    private static final Long TEST_TASK_ID = 2082333099000000099L;

    /** 预期 bucket 名 */
    private static final String EXPECTED_BUCKET = "finrpa-audit-" + TEST_ORG_ID;

    @Mock
    private MinioClient minioClient;

    private MinioProperties minioProperties;

    @InjectMocks
    private MinioStorageServiceImpl minioStorageService;

    @BeforeEach
    void setUp() {
        minioProperties = new MinioProperties();
        minioProperties.setEndpoint("http://localhost:9000");
        minioProperties.setAccessKey("minioadmin");
        minioProperties.setSecretKey("minioadmin");
        minioProperties.setBucketPrefix("finrpa-audit-");
        minioProperties.setPresignExpiryHours(1);
        minioProperties.setRetentionDays(90);
        minioStorageService = new MinioStorageServiceImpl();
        org.springframework.test.util.ReflectionTestUtils.setField(minioStorageService, "minioClient", minioClient);
        org.springframework.test.util.ReflectionTestUtils.setField(minioStorageService, "minioProperties", minioProperties);
    }

    // region uploadScreenshot

    @Test
    @DisplayName("上传截图 - bucket 已存在，直接上传并返回预签名 URL")
    void uploadScreenshot_BucketExists_Success() throws Exception {
        // 1. 准备 mock
        MultipartFile file = new MockMultipartFile("file", "screenshot.png", "image/png", new byte[]{1, 2, 3});
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                .thenReturn("https://minio.example.com/presigned-url");

        // 2. 执行
        ScreenshotUploadVO vo = minioStorageService.uploadScreenshot(file, TEST_ORG_ID, TEST_TASK_ID, 1, "before");

        // 3. 验证
        assertNotNull(vo);
        assertTrue(vo.getObjectPath().contains(String.valueOf(TEST_TASK_ID)));
        assertTrue(vo.getObjectPath().contains("1_before.png"));
        assertEquals("https://minio.example.com/presigned-url", vo.getPresignUrl());
        verify(minioClient, never()).makeBucket(any(MakeBucketArgs.class));
        verify(minioClient, times(1)).putObject(any(PutObjectArgs.class));
    }

    @Test
    @DisplayName("上传截图 - bucket 不存在，自动创建后上传")
    void uploadScreenshot_BucketNotExists_AutoCreate() throws Exception {
        MultipartFile file = new MockMultipartFile("file", "screenshot.png", "image/png", new byte[]{1, 2, 3});
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(false);
        when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                .thenReturn("https://minio.example.com/presigned-url");

        ScreenshotUploadVO vo = minioStorageService.uploadScreenshot(file, TEST_ORG_ID, TEST_TASK_ID, 2, "after");

        assertNotNull(vo);
        verify(minioClient, times(1)).makeBucket(any(MakeBucketArgs.class));
        verify(minioClient, times(1)).putObject(any(PutObjectArgs.class));
    }

    @Test
    @DisplayName("上传截图 - contentType 为空时默认 image/png")
    void uploadScreenshot_NullContentType_DefaultPng() throws Exception {
        MultipartFile file = new MockMultipartFile("file", "screenshot.png", null, new byte[]{1, 2, 3});
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                .thenReturn("https://minio.example.com/presigned-url");

        minioStorageService.uploadScreenshot(file, TEST_ORG_ID, TEST_TASK_ID, 1, "before");

        verify(minioClient, times(1)).putObject(any(PutObjectArgs.class));
    }

    @Test
    @DisplayName("上传截图 - 路径格式 {date}/{taskId}/{stepIndex}_{phase}.png")
    void uploadScreenshot_PathFormat() throws Exception {
        MultipartFile file = new MockMultipartFile("file", "screenshot.png", "image/png", new byte[]{1, 2, 3});
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                .thenReturn("url");

        ScreenshotUploadVO vo = minioStorageService.uploadScreenshot(file, TEST_ORG_ID, TEST_TASK_ID, 5, "before");

        // 验证路径格式：yyyy-MM-dd/{taskId}/5_before.png
        String path = vo.getObjectPath();
        assertTrue(path.matches("\\d{4}-\\d{2}-\\d{2}/" + TEST_TASK_ID + "/5_before\\.png"),
                "路径格式应为 {date}/{taskId}/{stepIndex}_{phase}.png，实际: " + path);
    }

    // endregion

    // region uploadScreenshot 参数校验

    @Test
    @DisplayName("上传截图 - 文件为空抛异常")
    void uploadScreenshot_EmptyFile_Throws() {
        MultipartFile file = new MockMultipartFile("file", "empty.png", "image/png", new byte[]{});
        BusinessException ex = assertThrows(BusinessException.class,
                () -> minioStorageService.uploadScreenshot(file, TEST_ORG_ID, TEST_TASK_ID, 1, "before"));
        assertTrue(ex.getMessage().contains("截图文件不能为空"));
    }

    @Test
    @DisplayName("上传截图 - orgId 为空抛异常")
    void uploadScreenshot_NullOrgId_Throws() {
        MultipartFile file = new MockMultipartFile("file", "screenshot.png", "image/png", new byte[]{1});
        BusinessException ex = assertThrows(BusinessException.class,
                () -> minioStorageService.uploadScreenshot(file, null, TEST_TASK_ID, 1, "before"));
        assertTrue(ex.getMessage().contains("组织 ID"));
    }

    @Test
    @DisplayName("上传截图 - taskId 为空抛异常")
    void uploadScreenshot_NullTaskId_Throws() {
        MultipartFile file = new MockMultipartFile("file", "screenshot.png", "image/png", new byte[]{1});
        BusinessException ex = assertThrows(BusinessException.class,
                () -> minioStorageService.uploadScreenshot(file, TEST_ORG_ID, null, 1, "before"));
        assertTrue(ex.getMessage().contains("任务 ID"));
    }

    @Test
    @DisplayName("上传截图 - stepIndex 为负抛异常")
    void uploadScreenshot_NegativeStepIndex_Throws() {
        MultipartFile file = new MockMultipartFile("file", "screenshot.png", "image/png", new byte[]{1});
        BusinessException ex = assertThrows(BusinessException.class,
                () -> minioStorageService.uploadScreenshot(file, TEST_ORG_ID, TEST_TASK_ID, -1, "before"));
        assertTrue(ex.getMessage().contains("步骤序号"));
    }

    @Test
    @DisplayName("上传截图 - phase 非法抛异常")
    void uploadScreenshot_InvalidPhase_Throws() {
        MultipartFile file = new MockMultipartFile("file", "screenshot.png", "image/png", new byte[]{1});
        BusinessException ex = assertThrows(BusinessException.class,
                () -> minioStorageService.uploadScreenshot(file, TEST_ORG_ID, TEST_TASK_ID, 1, "middle"));
        assertTrue(ex.getMessage().contains("阶段"));
    }

    @Test
    @DisplayName("上传截图 - putObject 失败抛 BusinessException")
    void uploadScreenshot_PutObjectFailed_Throws() throws Exception {
        MultipartFile file = new MockMultipartFile("file", "screenshot.png", "image/png", new byte[]{1, 2, 3});
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        when(minioClient.putObject(any(PutObjectArgs.class)))
                .thenThrow(new RuntimeException("MinIO 连接失败"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> minioStorageService.uploadScreenshot(file, TEST_ORG_ID, TEST_TASK_ID, 1, "before"));
        assertTrue(ex.getMessage().contains("截图上传 MinIO 失败"));
    }

    // endregion

    // region presignUrl

    @Test
    @DisplayName("预签名 URL - 成功生成")
    void presignUrl_Success() throws Exception {
        when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                .thenReturn("https://minio.example.com/presigned");

        String url = minioStorageService.presignUrl(TEST_ORG_ID, "2026-08-03/123/1_before.png");

        assertEquals("https://minio.example.com/presigned", url);
    }

    @Test
    @DisplayName("预签名 URL - orgId 为空抛异常")
    void presignUrl_NullOrgId_Throws() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> minioStorageService.presignUrl(null, "path"));
        assertTrue(ex.getMessage().contains("组织 ID"));
    }

    @Test
    @DisplayName("预签名 URL - 对象路径为空抛异常")
    void presignUrl_BlankPath_Throws() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> minioStorageService.presignUrl(TEST_ORG_ID, ""));
        assertTrue(ex.getMessage().contains("对象路径"));
    }

    @Test
    @DisplayName("预签名 URL - 生成失败抛 BusinessException")
    void presignUrl_GenerateFailed_Throws() throws Exception {
        when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                .thenThrow(new RuntimeException("签名失败"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> minioStorageService.presignUrl(TEST_ORG_ID, "path"));
        assertTrue(ex.getMessage().contains("预签名 URL 生成失败"));
    }

    // endregion

    // region deleteExpiredObjects

    @Test
    @DisplayName("清理过期 - 删除超过 90 天的对象，保留未过期对象")
    @SuppressWarnings("unchecked")
    void deleteExpiredObjects_DeletesExpiredOnly() throws Exception {
        // 1. mock bucket 列表（包含审计 bucket 和非审计 bucket）
        Bucket auditBucket = mock(Bucket.class);
        when(auditBucket.name()).thenReturn(EXPECTED_BUCKET);
        Bucket nonAuditBucket = mock(Bucket.class);
        when(nonAuditBucket.name()).thenReturn("other-bucket");
        when(minioClient.listBuckets()).thenReturn(Arrays.asList(auditBucket, nonAuditBucket));

        // 2. mock 对象列表（一个过期，一个未过期）
        Item expiredItem = mock(Item.class);
        when(expiredItem.lastModified()).thenReturn(ZonedDateTime.now().minusDays(100));
        when(expiredItem.objectName()).thenReturn("2026-05-01/123/1_before.png");

        Item freshItem = mock(Item.class);
        when(freshItem.lastModified()).thenReturn(ZonedDateTime.now().minusDays(10));

        Result<Item> expiredResult = mock(Result.class);
        when(expiredResult.get()).thenReturn(expiredItem);
        Result<Item> freshResult = mock(Result.class);
        when(freshResult.get()).thenReturn(freshItem);

        when(minioClient.listObjects(any(ListObjectsArgs.class)))
                .thenReturn(Arrays.asList(expiredResult, freshResult));

        // 3. 执行
        int deleted = minioStorageService.deleteExpiredObjects();

        // 4. 验证：仅删除过期对象
        assertEquals(1, deleted);
        verify(minioClient, times(1)).removeObject(any(RemoveObjectArgs.class));
    }

    @Test
    @DisplayName("清理过期 - 无过期对象时返回 0")
    @SuppressWarnings("unchecked")
    void deleteExpiredObjects_NoExpired() throws Exception {
        Bucket auditBucket = mock(Bucket.class);
        when(auditBucket.name()).thenReturn(EXPECTED_BUCKET);
        when(minioClient.listBuckets()).thenReturn(Collections.singletonList(auditBucket));

        Item freshItem = mock(Item.class);
        when(freshItem.lastModified()).thenReturn(ZonedDateTime.now().minusDays(5));
        Result<Item> freshResult = mock(Result.class);
        when(freshResult.get()).thenReturn(freshItem);
        when(minioClient.listObjects(any(ListObjectsArgs.class)))
                .thenReturn(Collections.singletonList(freshResult));

        int deleted = minioStorageService.deleteExpiredObjects();

        assertEquals(0, deleted);
        verify(minioClient, never()).removeObject(any(RemoveObjectArgs.class));
    }

    @Test
    @DisplayName("清理过期 - 非审计 bucket 被跳过")
    @SuppressWarnings("unchecked")
    void deleteExpiredObjects_SkipNonAuditBucket() throws Exception {
        Bucket nonAuditBucket = mock(Bucket.class);
        when(nonAuditBucket.name()).thenReturn("other-bucket");
        when(minioClient.listBuckets()).thenReturn(Collections.singletonList(nonAuditBucket));

        int deleted = minioStorageService.deleteExpiredObjects();

        assertEquals(0, deleted);
        verify(minioClient, never()).listObjects(any(ListObjectsArgs.class));
    }

    @Test
    @DisplayName("清理过期 - listBuckets 异常时返回 0 不抛出")
    void deleteExpiredObjects_ListBucketsError_ReturnsZero() throws Exception {
        when(minioClient.listBuckets()).thenThrow(new RuntimeException("MinIO 不可用"));

        int deleted = minioStorageService.deleteExpiredObjects();

        assertEquals(0, deleted);
    }

    // endregion
}
