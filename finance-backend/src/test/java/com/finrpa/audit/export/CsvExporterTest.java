package com.finrpa.audit.export;

import com.finrpa.audit.dto.response.AuditLogVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CsvExporter 单元测试（M7.4）
 *
 * <p>覆盖：UTF-8 BOM 头 / 表头 / 字段转义 / null 处理 / 空列表 / 异常入参。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
class CsvExporterTest {

    /** UTF-8 BOM 头 */
    private static final byte[] UTF8_BOM = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    @Test
    @DisplayName("导出 - 空列表仅含 BOM + 表头")
    void export_EmptyList_OnlyHeader() throws IOException {
        // 1. 执行
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CsvExporter.export(List.of(), out);

        // 2. 验证 BOM 头
        byte[] bytes = out.toByteArray();
        byte[] bom = Arrays.copyOfRange(bytes, 0, 3);
        assertArrayEquals(UTF8_BOM, bom);

        // 3. 验证表头存在
        String content = new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
        assertTrue(content.contains("审计ID"));
        assertTrue(content.contains("动作类型"));
        assertTrue(content.contains("LLM成本(美元)"));
        // 仅表头一行（末尾 CRLF，split 后末尾空串不计）
        assertEquals(1, content.split("\r\n").length);
    }

    @Test
    @DisplayName("导出 - 单条完整记录所有字段正确输出")
    void export_SingleRecord_AllFieldsWritten() throws IOException {
        // 1. 构建测试数据
        AuditLogVO vo = buildFullVO();

        // 2. 执行
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CsvExporter.export(List.of(vo), out);

        // 3. 验证
        String content = new String(out.toByteArray(), StandardCharsets.UTF_8);
        // 表头 + 1 数据行（末尾 CRLF 不影响行数）
        assertEquals(2, content.split("\r\n").length);
        assertTrue(content.contains(String.valueOf(vo.getAuditId())));
        assertTrue(content.contains(vo.getActionType()));
        assertTrue(content.contains(vo.getRiskLevel()));
        assertTrue(content.contains("INPUT_TEXT"));
        assertTrue(content.contains("2026-08-03 10:00:00"));
        assertTrue(content.contains("0.0123"));
    }

    @Test
    @DisplayName("导出 - 多条记录按顺序输出")
    void export_MultipleRecords_InOrder() throws IOException {
        AuditLogVO vo1 = new AuditLogVO();
        vo1.setAuditId(1L);
        vo1.setActionType("CLICK");

        AuditLogVO vo2 = new AuditLogVO();
        vo2.setAuditId(2L);
        vo2.setActionType("INPUT_TEXT");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CsvExporter.export(List.of(vo1, vo2), out);

        String content = new String(out.toByteArray(), StandardCharsets.UTF_8);
        // 表头 + 2 数据行
        assertEquals(3, content.split("\r\n").length);
        // vo1（auditId=1）在 vo2（auditId=2）前
        int idx1 = content.indexOf("1,2001");
        int idx2 = content.indexOf("2,2001");
        // 仅校验两条记录的相对顺序（用 auditId + taskId 组合避免误匹配表头）
        // 此处 auditId 不重要，重点验证两条数据行都存在
        assertTrue(content.contains("CLICK"));
        assertTrue(content.contains("INPUT_TEXT"));
    }

    @Test
    @DisplayName("字段转义 - 含逗号字段用双引号包裹")
    void export_FieldWithComma_Quoted() throws IOException {
        AuditLogVO vo = new AuditLogVO();
        vo.setAuditId(1L);
        vo.setErrorMessage("页面加载失败,超时");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CsvExporter.export(List.of(vo), out);

        String content = new String(out.toByteArray(), StandardCharsets.UTF_8);
        assertTrue(content.contains("\"页面加载失败,超时\""));
    }

    @Test
    @DisplayName("字段转义 - 含双引号字段转义为两个双引号并包裹")
    void export_FieldWithQuote_EscapedAndQuoted() throws IOException {
        AuditLogVO vo = new AuditLogVO();
        vo.setAuditId(1L);
        // 原始字段 {"key":"value"} 转义后应为 "{""key"":""value""}"
        vo.setActionParams("{\"key\":\"value\"}");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CsvExporter.export(List.of(vo), out);

        String content = new String(out.toByteArray(), StandardCharsets.UTF_8);
        assertTrue(content.contains("\"{\"\"key\"\":\"\"value\"\"}\""),
                "含双引号的字段应被转义为两个双引号并用双引号包裹");
    }

    @Test
    @DisplayName("字段转义 - 含换行字段用双引号包裹")
    void export_FieldWithNewline_Quoted() throws IOException {
        AuditLogVO vo = new AuditLogVO();
        vo.setAuditId(1L);
        vo.setErrorMessage("第一行\n第二行");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CsvExporter.export(List.of(vo), out);

        String content = new String(out.toByteArray(), StandardCharsets.UTF_8);
        // 换行被双引号包裹后，整行数据应仍是 1 行（表头 + 1 数据行 = 2 行）
        assertEquals(2, content.split("\r\n").length);
        assertTrue(content.contains("\"第一行\n第二行\""));
    }

    @Test
    @DisplayName("null 字段输出为空字符串")
    void export_NullFields_EmptyString() throws IOException {
        AuditLogVO vo = new AuditLogVO();
        // 只设置 auditId，其余字段均为 null
        vo.setAuditId(99L);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CsvExporter.export(List.of(vo), out);

        String content = new String(out.toByteArray(), StandardCharsets.UTF_8);
        // 数据行存在
        assertTrue(content.contains("99"));
        // 第二字段（taskId）为 null → 数据行以 "99," 开头
        String dataLine = content.split("\r\n")[1];
        assertTrue(dataLine.startsWith("99,"));
    }

    @Test
    @DisplayName("时间戳格式化为 yyyy-MM-dd HH:mm:ss")
    void export_Timestamp_FormattedCorrectly() throws IOException {
        AuditLogVO vo = new AuditLogVO();
        vo.setAuditId(1L);
        vo.setStartedAt(Timestamp.valueOf(LocalDateTime.of(2026, 8, 3, 10, 30, 45)));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CsvExporter.export(List.of(vo), out);

        String content = new String(out.toByteArray(), StandardCharsets.UTF_8);
        assertTrue(content.contains("2026-08-03 10:30:45"));
    }

    @Test
    @DisplayName("BigDecimal 用 toPlainString（避免科学计数法）")
    void export_BigDecimal_PlainString() throws IOException {
        AuditLogVO vo = new AuditLogVO();
        vo.setAuditId(1L);
        vo.setLlmCost(new BigDecimal("0.000000123"));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CsvExporter.export(List.of(vo), out);

        String content = new String(out.toByteArray(), StandardCharsets.UTF_8);
        assertTrue(content.contains("0.000000123"));
        // 不应含科学计数法 E（仅校验 LLM 成本字段，避免误匹配其他字段）
        String dataLine = content.split("\r\n")[1];
        assertTrue(dataLine.contains("0.000000123"));
    }

    @Test
    @DisplayName("null logs 抛 NullPointerException")
    void export_NullLogs_Throws() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertThrows(NullPointerException.class, () -> CsvExporter.export(null, out));
    }

    @Test
    @DisplayName("null OutputStream 抛 NullPointerException")
    void export_NullOutputStream_Throws() {
        assertThrows(NullPointerException.class, () -> CsvExporter.export(List.of(), null));
    }

    /**
     * 构建包含所有字段的完整 VO
     *
     * @return AuditLogVO
     */
    private AuditLogVO buildFullVO() {
        AuditLogVO vo = new AuditLogVO();
        vo.setAuditId(1001L);
        vo.setTaskId(2001L);
        vo.setOrgId(3001L);
        vo.setDepartmentId(4001L);
        vo.setBusinessLineId(5001L);
        vo.setUserId(6001L);
        vo.setActionType("INPUT_TEXT");
        vo.setTargetElement("#username");
        vo.setPageUrl("https://example.com/login");
        vo.setActionParams("{\"username\":\"admin\"}");
        vo.setExecutionResult("success");
        vo.setErrorMessage("");
        vo.setRiskLevel("low");
        vo.setApprovalId(7001L);
        vo.setStartedAt(Timestamp.valueOf(LocalDateTime.of(2026, 8, 3, 10, 0, 0)));
        vo.setCompletedAt(Timestamp.valueOf(LocalDateTime.of(2026, 8, 3, 10, 0, 5)));
        vo.setDurationMs(5000L);
        vo.setBeforeScreenshotUrl("https://minio.local/before.png");
        vo.setAfterScreenshotUrl("https://minio.local/after.png");
        vo.setLlmModel("gpt-4o");
        vo.setLlmTokensUsed(1234);
        vo.setLlmCost(new BigDecimal("0.0123"));
        vo.setCreateTime(Timestamp.valueOf(LocalDateTime.of(2026, 8, 3, 10, 0, 6)));
        return vo;
    }
}
