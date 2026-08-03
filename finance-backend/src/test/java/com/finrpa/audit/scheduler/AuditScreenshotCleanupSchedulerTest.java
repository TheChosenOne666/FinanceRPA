package com.finrpa.audit.scheduler;

import com.finrpa.audit.storage.MinioStorageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

/**
 * 审计截图过期清理调度器单元测试（M7.2）
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@ExtendWith(MockitoExtension.class)
class AuditScreenshotCleanupSchedulerTest {

    @Mock
    private MinioStorageService minioStorageService;

    @InjectMocks
    private AuditScreenshotCleanupScheduler scheduler;

    @Test
    @DisplayName("清理过期截图 - 调用 storageService.deleteExpiredObjects")
    void cleanupExpiredScreenshots_CallsService() {
        when(minioStorageService.deleteExpiredObjects()).thenReturn(5);

        scheduler.cleanupExpiredScreenshots();

        verify(minioStorageService, times(1)).deleteExpiredObjects();
    }

    @Test
    @DisplayName("清理过期截图 - 服务异常时不抛出（调度器容错）")
    void cleanupExpiredScreenshots_ServiceThrows_NoException() {
        when(minioStorageService.deleteExpiredObjects())
                .thenThrow(new RuntimeException("MinIO 不可用"));

        // 调度器内部 try-catch，不向外抛出
        assertDoesNotThrow(() -> scheduler.cleanupExpiredScreenshots());

        verify(minioStorageService, times(1)).deleteExpiredObjects();
    }

    @Test
    @DisplayName("清理过期截图 - 无过期对象时正常返回")
    void cleanupExpiredScreenshots_NoExpired() {
        when(minioStorageService.deleteExpiredObjects()).thenReturn(0);

        scheduler.cleanupExpiredScreenshots();

        verify(minioStorageService, times(1)).deleteExpiredObjects();
    }
}
