package com.finrpa.system.controller;

import com.finrpa.ai.config.AiServiceProperties;
import com.finrpa.audit.config.MinioProperties;
import com.finrpa.common.response.BaseResponse;
import com.finrpa.common.response.ResultUtils;
import com.finrpa.system.dto.request.SystemConfigUpdateRequest;
import com.finrpa.system.dto.response.SystemConfigVO;
import com.finrpa.system.service.SystemConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 系统配置控制器（P3 统一配置中心）
 *
 * <p>对外端点（实际访问路径前缀 {@code /api/system-config}）：
 * <ul>
 *   <li>GET /system-config —— 查询全部配置项（设置页展示）</li>
 *   <li>PUT /system-config/{key} —— 按 config_key 更新配置（运行时热生效）</li>
 *   <li>POST /system-config/refresh —— 手动刷新缓存并重建配置属性（高频字段立即生效）</li>
 * </ul>
 * </p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Slf4j
@RestController
@RequestMapping("/system-config")
@Tag(name = "系统配置管理", description = "统一管理 AI/MinIO/定时任务/系统开关等配置（P3）")
public class SystemConfigController {

    /** 系统配置服务 */
    @Resource
    private SystemConfigService systemConfigService;

    /** AI 服务配置属性（INT-1 刷新） */
    @Resource
    private AiServiceProperties aiServiceProperties;

    /** MinIO 配置属性（INT-3 刷新） */
    @Resource
    private MinioProperties minioProperties;

    // region 查询

    /**
     * 查询全部配置项
     *
     * @return 配置列表
     */
    @GetMapping
    @Operation(summary = "配置列表", description = "查询全部系统配置项")
    public BaseResponse<List<SystemConfigVO>> listAll() {
        return ResultUtils.success(systemConfigService.listAll());
    }

    // endregion

    // region 更新

    /**
     * 按 config_key 更新配置
     *
     * @param key     配置键
     * @param request 更新请求
     * @return 更新后的配置 VO
     */
    @PutMapping("/{key}")
    @Operation(summary = "更新配置", description = "按 config_key 更新配置值，运行时热生效")
    public BaseResponse<SystemConfigVO> updateConfig(
            @PathVariable String key,
            @Valid @RequestBody SystemConfigUpdateRequest request) {
        return ResultUtils.success(systemConfigService.updateConfig(key, request));
    }

    /**
     * 手动刷新缓存并重建配置属性
     *
     * <p>流程：
     * <ol>
     *   <li>刷新 SystemConfigService 本地缓存（重载 sys_config 全表）</li>
     *   <li>调用 AiServiceProperties.refreshFromConfig：高频字段（retry.maxAttempts）立即热生效</li>
     *   <li>调用 MinioProperties.refreshFromConfig：高频字段（presignExpiryHours 等）立即热生效</li>
     * </ol>
     * 注：连接参数（ai.base_url / minio.endpoint 等）刷新后需重启重建 WebClient/MinioClient。</p>
     *
     * @return 操作结果
     */
    @PostMapping("/refresh")
    @Operation(summary = "刷新缓存", description = "刷新配置缓存并重建 AI/MinIO 配置属性（高频字段立即生效）")
    public BaseResponse<Boolean> refresh() {
        // 1. 刷新 SystemConfigService 本地缓存
        systemConfigService.refreshCache();
        // 2. 重建 AI 配置属性（高频字段热生效）
        aiServiceProperties.refreshFromConfig(systemConfigService);
        // 3. 重建 MinIO 配置属性（高频字段热生效）
        minioProperties.refreshFromConfig(systemConfigService);
        log.info("系统配置已刷新：AI/MinIO 高频字段已热生效，连接参数改动需重启");
        return ResultUtils.success(true);
    }

    // endregion
}
