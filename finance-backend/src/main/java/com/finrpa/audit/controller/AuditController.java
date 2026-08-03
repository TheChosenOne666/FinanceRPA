package com.finrpa.audit.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.finrpa.audit.constant.AuditConstant;
import com.finrpa.audit.dto.request.AuditLogQueryRequest;
import com.finrpa.audit.dto.response.AuditLogVO;
import com.finrpa.audit.export.CsvExporter;
import com.finrpa.audit.service.AuditLogService;
import com.finrpa.common.response.BaseResponse;
import com.finrpa.common.response.ResultUtils;
import com.finrpa.tenant.context.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 审计日志控制器（对外 API）（M7.1，M7.4 增强）
 *
 * <p>对外端点（实际访问路径前缀 {@code /api}）：
 * <ul>
 *   <li>GET /v1/audit/logs —— 分页多维检索审计日志（时间范围/任务/用户/部门/业务线/风险等级/操作类型/排序）</li>
 *   <li>GET /v1/audit/logs/{auditId} —— 查询审计日志详情</li>
 *   <li>GET /v1/audit/logs/export —— 导出 CSV（M7.4）</li>
 * </ul>
 * </p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Slf4j
@RestController
@RequestMapping("/v1/audit/logs")
@Tag(name = "审计日志", description = "全链路审计日志多维检索、详情查询与 CSV 导出")
public class AuditController {

    /** 审计日志服务 */
    @Resource
    private AuditLogService auditLogService;

    // region 查询

    /**
     * 分页多维检索审计日志（M7.4 增强排序）
     *
     * <p>支持 sortField/sortOrder 动态排序，字段必须命中白名单。</p>
     *
     * @param queryRequest 检索请求
     * @return 审计日志分页列表
     */
    @GetMapping
    @Operation(summary = "审计日志列表", description = "分页多维检索审计日志，支持时间范围/任务/用户/部门/业务线/风险等级/操作类型筛选 + 动态排序")
    public BaseResponse<IPage<AuditLogVO>> listAuditLogs(AuditLogQueryRequest queryRequest) {
        // 从登录上下文获取 orgId（租户隔离）
        String orgIdStr = TenantContext.getOrgId();
        if (orgIdStr != null) {
            queryRequest.setOrgId(Long.parseLong(orgIdStr));
        }
        return ResultUtils.success(auditLogService.listAuditLogs(queryRequest));
    }

    /**
     * 导出审计日志为 CSV（M7.4）
     *
     * <p>按查询条件全量导出（最多 {@link AuditConstant#EXPORT_MAX_ROWS} 条），
     * 直接以 {@code text/csv} 文件流写入 HttpServletResponse。</p>
     *
     * <p>注意：此端点路径为 {@code /v1/audit/logs/export}，需放在 {@code /{auditId}} 之前
     * 避免 "export" 被当作 path variable 解析。</p>
     *
     * @param queryRequest 检索请求（同列表查询条件）
     * @param response     HTTP 响应（用于写入 CSV 流）
     * @throws IOException 写入失败时抛出
     */
    @GetMapping("/export")
    @Operation(summary = "导出审计日志 CSV", description = "按查询条件导出审计日志为 CSV 文件（UTF-8 with BOM，Excel 兼容）")
    public void exportAuditLogs(AuditLogQueryRequest queryRequest, HttpServletResponse response)
            throws IOException {
        // 1. 从登录上下文获取 orgId（租户隔离）
        String orgIdStr = TenantContext.getOrgId();
        if (orgIdStr != null) {
            queryRequest.setOrgId(Long.parseLong(orgIdStr));
        }

        // 2. 查询导出数据
        List<AuditLogVO> logs = auditLogService.exportAuditLogs(queryRequest);
        log.info("CSV 导出请求: orgId={}, 条数={}", queryRequest.getOrgId(), logs.size());

        // 3. 设置响应头（CSV 文件下载）
        String fileName = buildExportFileName();
        response.setContentType(AuditConstant.EXPORT_CONTENT_TYPE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        // Content-Disposition: 文件名 URL 编码（兼容中文文件名）
        String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8);
        response.setHeader("Content-Disposition",
                "attachment; filename=\"" + encodedFileName + "\"; filename*=UTF-8''" + encodedFileName);

        // 4. 写入 CSV 流（CsvExporter 内部会写 UTF-8 BOM）
        try (OutputStream os = response.getOutputStream()) {
            CsvExporter.export(logs, os);
            os.flush();
        } catch (IOException e) {
            log.error("CSV 导出失败: orgId={}, error={}", queryRequest.getOrgId(), e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 查询审计日志详情
     *
     * @param auditId 审计日志业务 ID
     * @return 审计日志详情
     */
    @GetMapping("/{auditId}")
    @Operation(summary = "审计日志详情", description = "按 auditId 查询审计日志详情")
    public BaseResponse<AuditLogVO> getAuditLogDetail(@PathVariable Long auditId) {
        return ResultUtils.success(auditLogService.getAuditLogDetail(auditId));
    }

    // endregion

    // region 私有方法

    /**
     * 构建导出文件名（audit_logs_yyyyMMdd.csv）
     *
     * @return 文件名
     */
    private String buildExportFileName() {
        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        return AuditConstant.EXPORT_FILE_NAME_PREFIX + dateStr + ".csv";
    }

    // endregion
}
