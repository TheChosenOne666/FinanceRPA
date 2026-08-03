package com.finrpa.audit.export;

import com.finrpa.audit.dto.response.AuditLogVO;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

/**
 * 审计日志 CSV 导出器（M7.4）
 *
 * <p>将 {@link AuditLogVO} 列表序列化为 CSV 字节流，写入到给定 {@link OutputStream}。
 * 不引入第三方 CSV 库，使用 JDK 原生实现 + RFC 4180 转义规则。</p>
 *
 * <p>格式约定：
 * <ul>
 *   <li>UTF-8 with BOM（Excel 中文不乱码）</li>
 *   <li>字段顺序固定（对齐系统设计 6.4.1 六大维度）</li>
 *   <li>含逗号 / 双引号 / 换行的字段用双引号包裹，内部双引号转义为 {@code ""}</li>
 *   <li>null 输出为空字符串</li>
 * </ul>
 * </p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
public final class CsvExporter {

    /** CSV 字段分隔符 */
    private static final char FIELD_SEPARATOR = ',';

    /** CSV 字段包裹符 */
    private static final char QUOTE_CHAR = '"';

    /** CSV 行结束符（RFC 4180 规定 CRLF） */
    private static final String LINE_SEPARATOR = "\r\n";

    /** UTF-8 BOM 头（Excel 识别 UTF-8 中文） */
    private static final byte[] UTF8_BOM = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    /** 时间戳格式化（ISO 标准格式，便于排序） */
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** CSV 表头（与字段顺序一致） */
    private static final String[] HEADERS = {
            "审计ID", "任务ID", "组织ID", "部门ID", "业务线ID", "用户ID",
            "动作类型", "目标元素", "页面URL", "操作参数", "执行结果", "错误信息",
            "风险等级", "审批单ID",
            "开始时间", "完成时间", "耗时(ms)",
            "操作前截图URL", "操作后截图URL",
            "LLM模型", "LLM token用量", "LLM成本(美元)",
            "创建时间"
    };

    /** 私有构造，工具类不允许实例化 */
    private CsvExporter() {
        throw new UnsupportedOperationException("CsvExporter 是工具类，不允许实例化");
    }

    /**
     * 将审计日志列表导出为 CSV 字节流
     *
     * @param logs          审计日志列表（不允许为 null）
     * @param outputStream  输出流（不允许为 null，调用方负责关闭）
     * @throws IOException 写入失败时抛出
     */
    public static void export(List<AuditLogVO> logs, OutputStream outputStream) throws IOException {
        Objects.requireNonNull(logs, "审计日志列表不能为空");
        Objects.requireNonNull(outputStream, "输出流不能为空");

        // 1. 写入 UTF-8 BOM（Excel 中文识别）
        outputStream.write(UTF8_BOM);

        // 2. 使用 UTF-8 编码的 Writer
        try (Writer writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)) {
            // 3. 写表头
            writeRow(writer, HEADERS);

            // 4. 写数据行
            for (AuditLogVO log : logs) {
                writeRow(writer, toRow(log));
            }
            writer.flush();
        }
    }

    /**
     * 将单条审计日志转为 CSV 行字段数组
     *
     * @param log 审计日志
     * @return 字段数组（顺序与 {@link #HEADERS} 一致）
     */
    private static String[] toRow(AuditLogVO log) {
        return new String[]{
                toStr(log.getAuditId()),
                toStr(log.getTaskId()),
                toStr(log.getOrgId()),
                toStr(log.getDepartmentId()),
                toStr(log.getBusinessLineId()),
                toStr(log.getUserId()),
                log.getActionType(),
                log.getTargetElement(),
                log.getPageUrl(),
                log.getActionParams(),
                log.getExecutionResult(),
                log.getErrorMessage(),
                log.getRiskLevel(),
                toStr(log.getApprovalId()),
                toTimestamp(log.getStartedAt()),
                toTimestamp(log.getCompletedAt()),
                toStr(log.getDurationMs()),
                log.getBeforeScreenshotUrl(),
                log.getAfterScreenshotUrl(),
                log.getLlmModel(),
                toStr(log.getLlmTokensUsed()),
                toStr(log.getLlmCost()),
                toTimestamp(log.getCreateTime())
        };
    }

    /**
     * 写一行 CSV（字段按 RFC 4180 转义后用逗号拼接，末尾加 CRLF）
     *
     * @param writer 输出 Writer
     * @param fields 字段值数组
     * @throws IOException 写入失败
     */
    private static void writeRow(Writer writer, String[] fields) throws IOException {
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) {
                writer.write(FIELD_SEPARATOR);
            }
            writer.write(escape(fields[i]));
        }
        writer.write(LINE_SEPARATOR);
    }

    /**
     * 字段转义：含逗号 / 双引号 / 换行 / 回车时用双引号包裹，内部双引号转义为 {@code ""}
     *
     * @param field 原始字段值（可能为 null）
     * @return 转义后的字段
     */
    private static String escape(String field) {
        if (field == null) {
            return "";
        }
        boolean needQuote = field.indexOf(FIELD_SEPARATOR) >= 0
                || field.indexOf(QUOTE_CHAR) >= 0
                || field.indexOf('\n') >= 0
                || field.indexOf('\r') >= 0;
        if (!needQuote) {
            return field;
        }
        // 双引号转义为两个双引号，再用双引号包裹
        return QUOTE_CHAR + field.replace(String.valueOf(QUOTE_CHAR), "\"\"") + QUOTE_CHAR;
    }

    /**
     * Long 转 String（null 返回空字符串）
     *
     * @param value Long 值
     * @return 字符串
     */
    private static String toStr(Long value) {
        return value == null ? "" : value.toString();
    }

    /**
     * Integer 转 String（null 返回空字符串）
     *
     * @param value Integer 值
     * @return 字符串
     */
    private static String toStr(Integer value) {
        return value == null ? "" : value.toString();
    }

    /**
     * BigDecimal 转 String（null 返回空字符串）
     *
     * @param value BigDecimal 值
     * @return 字符串
     */
    private static String toStr(java.math.BigDecimal value) {
        return value == null ? "" : value.toPlainString();
    }

    /**
     * Timestamp 转 String（null 返回空字符串）
     *
     * @param ts Timestamp 值
     * @return 格式化字符串（yyyy-MM-dd HH:mm:ss）
     */
    private static String toTimestamp(java.sql.Timestamp ts) {
        if (ts == null) {
            return "";
        }
        return ts.toLocalDateTime().format(TIMESTAMP_FORMATTER);
    }
}
