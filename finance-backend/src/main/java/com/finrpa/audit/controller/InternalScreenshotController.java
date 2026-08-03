package com.finrpa.audit.controller;

import com.finrpa.audit.dto.response.ScreenshotUploadVO;
import com.finrpa.audit.storage.MinioStorageService;
import com.finrpa.common.response.BaseResponse;
import com.finrpa.common.response.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 截图内部回调控制器（Python → Java → MinIO）（M7.2，ADR-004）
 *
 * <p>Python Executor 执行任务时，每步操作前后截图通过此控制器上传到 MinIO。
 * 鉴权由 {@link com.finrpa.agent.interceptor.InternalTokenInterceptor} 拦截 {@code X-Internal-Token} Header 完成。</p>
 *
 * <p>内部端点（实际访问路径前缀 {@code /api}）：
 * <ul>
 *   <li>POST /internal/screenshots —— 上传截图（multipart/form-data）</li>
 * </ul>
 * </p>
 *
 * <p>数据流（系统设计 6.4.2）：
 * <ol>
 *   <li>Python 操作前截图 → 上传 Java → Java 转发 MinIO → 返回预签名 URL</li>
 *   <li>Python 执行操作</li>
 *   <li>Python 操作后截图 → 上传 Java → Java 转发 MinIO → 返回预签名 URL</li>
 *   <li>Python 上报审计元数据（POST /internal/audit/logs），携带预签名 URL</li>
 * </ol>
 * </p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Slf4j
@RestController
@RequestMapping("/internal/screenshots")
@Tag(name = "截图内部回调", description = "Python AI 服务上传审计截图到 MinIO（X-Internal-Token 鉴权）")
public class InternalScreenshotController {

    /** MinIO 截图存储服务 */
    @Resource
    private MinioStorageService minioStorageService;

    /**
     * 上传截图（Python 回调，multipart/form-data）
     *
     * <p>请求格式：multipart/form-data
     * <ul>
     *   <li>file: 截图文件（PNG）</li>
     *   <li>orgId: 组织 ID</li>
     *   <li>taskId: 任务 ID</li>
     *   <li>stepIndex: 步骤序号</li>
     *   <li>phase: 阶段（before / after）</li>
     * </ul>
     * </p>
     *
     * @param file      截图文件
     * @param orgId     组织 ID
     * @param taskId    任务 ID
     * @param stepIndex 步骤序号
     * @param phase     阶段：before / after
     * @return 上传结果（含对象路径与预签名 URL）
     */
    @PostMapping
    @Operation(summary = "上传截图", description = "Python Executor 上传操作前后截图到 MinIO，返回预签名 URL 供后续审计日志引用")
    public BaseResponse<ScreenshotUploadVO> uploadScreenshot(
            @RequestPart("file") MultipartFile file,
            @RequestParam("orgId") Long orgId,
            @RequestParam("taskId") Long taskId,
            @RequestParam("stepIndex") Integer stepIndex,
            @RequestParam("phase") String phase) {
        log.info("[InternalScreenshot] 收到截图上传: orgId={}, taskId={}, stepIndex={}, phase={}, size={}bytes",
                orgId, taskId, stepIndex, phase, file.getSize());
        ScreenshotUploadVO vo = minioStorageService.uploadScreenshot(file, orgId, taskId, stepIndex, phase);
        return ResultUtils.success(vo);
    }
}
