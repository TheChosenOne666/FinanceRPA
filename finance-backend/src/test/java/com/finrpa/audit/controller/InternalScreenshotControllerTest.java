package com.finrpa.audit.controller;

import com.finrpa.audit.dto.response.ScreenshotUploadVO;
import com.finrpa.audit.storage.MinioStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.multipart.MultipartFile;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 截图内部回调控制器单元测试（M7.2）
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
class InternalScreenshotControllerTest {

    /** 测试用组织 ID */
    private static final Long TEST_ORG_ID = 2082342545947660289L;

    /** 测试用任务 ID */
    private static final Long TEST_TASK_ID = 2082333099000000099L;

    private MockMvc mockMvc;
    private MinioStorageService minioStorageService;

    @BeforeEach
    void setUp() {
        minioStorageService = mock(MinioStorageService.class);
        InternalScreenshotController controller = new InternalScreenshotController();
        org.springframework.test.util.ReflectionTestUtils.setField(controller, "minioStorageService", minioStorageService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("上传截图 - before 阶段成功")
    void uploadScreenshot_BeforePhase_Success() throws Exception {
        // 1. mock
        ScreenshotUploadVO vo = new ScreenshotUploadVO();
        vo.setObjectPath("2026-08-03/" + TEST_TASK_ID + "/1_before.png");
        vo.setPresignUrl("https://minio.example.com/presigned");
        when(minioStorageService.uploadScreenshot(any(MultipartFile.class), eq(TEST_ORG_ID), eq(TEST_TASK_ID), eq(1), eq("before")))
                .thenReturn(vo);

        // 2. 执行并验证
        mockMvc.perform(multipart("/internal/screenshots")
                        .file(new MockMultipartFile("file", "screenshot.png", "image/png", new byte[]{1, 2, 3}))
                        .param("orgId", String.valueOf(TEST_ORG_ID))
                        .param("taskId", String.valueOf(TEST_TASK_ID))
                        .param("stepIndex", "1")
                        .param("phase", "before"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.objectPath").value("2026-08-03/" + TEST_TASK_ID + "/1_before.png"))
                .andExpect(jsonPath("$.data.presignUrl").value("https://minio.example.com/presigned"));

        verify(minioStorageService, times(1)).uploadScreenshot(any(), eq(TEST_ORG_ID), eq(TEST_TASK_ID), eq(1), eq("before"));
    }

    @Test
    @DisplayName("上传截图 - after 阶段成功")
    void uploadScreenshot_AfterPhase_Success() throws Exception {
        ScreenshotUploadVO vo = new ScreenshotUploadVO();
        vo.setObjectPath("2026-08-03/" + TEST_TASK_ID + "/1_after.png");
        vo.setPresignUrl("https://minio.example.com/presigned");
        when(minioStorageService.uploadScreenshot(any(MultipartFile.class), anyLong(), anyLong(), anyInt(), eq("after")))
                .thenReturn(vo);

        mockMvc.perform(multipart("/internal/screenshots")
                        .file(new MockMultipartFile("file", "screenshot.png", "image/png", new byte[]{1, 2, 3}))
                        .param("orgId", String.valueOf(TEST_ORG_ID))
                        .param("taskId", String.valueOf(TEST_TASK_ID))
                        .param("stepIndex", "1")
                        .param("phase", "after"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.objectPath").value("2026-08-03/" + TEST_TASK_ID + "/1_after.png"));
    }
}
