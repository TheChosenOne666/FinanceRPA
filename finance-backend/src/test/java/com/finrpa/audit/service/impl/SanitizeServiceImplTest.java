package com.finrpa.audit.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 脱敏服务实现单元测试（系统设计 6.4.3）
 *
 * <p>覆盖银行卡 / 身份证 / 密码 / 手机 / 邮箱 五类规则，以及 action_params JSON 整体脱敏。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
class SanitizeServiceImplTest {

    private SanitizeServiceImpl sanitizeService;

    @BeforeEach
    void setUp() {
        sanitizeService = new SanitizeServiceImpl();
        ReflectionTestUtils.setField(sanitizeService, "objectMapper", new ObjectMapper());
    }

    // region 银行卡脱敏

    @Test
    @DisplayName("银行卡脱敏 - 16 位标准卡号前 4 后 4")
    void sanitizeCard_Standard() {
        assertEquals("6225********1234", sanitizeService.sanitizeCard("6225751212341234"));
    }

    @Test
    @DisplayName("银行卡脱敏 - 19 位卡号")
    void sanitizeCard_19Digits() {
        // 19 位卡号：前 4 位 6225 + 11 个 * + 末 4 位 4123
        assertEquals("6225***********4123", sanitizeService.sanitizeCard("6225751212341234123"));
    }

    @Test
    @DisplayName("银行卡脱敏 - null 返回 ***")
    void sanitizeCard_Null() {
        assertEquals("***", sanitizeService.sanitizeCard(null));
    }

    @Test
    @DisplayName("银行卡脱敏 - 长度不足 9 返回 ***")
    void sanitizeCard_TooShort() {
        assertEquals("***", sanitizeService.sanitizeCard("123"));
    }

    // endregion

    // region 身份证脱敏

    @Test
    @DisplayName("身份证脱敏 - 18 位纯数字前 6 后 4")
    void sanitizeIdCard_18Digits() {
        assertEquals("110101********1234", sanitizeService.sanitizeIdCard("110101199001011234"));
    }

    @Test
    @DisplayName("身份证脱敏 - 末位 X")
    void sanitizeIdCard_EndsWithX() {
        assertEquals("110101********123X", sanitizeService.sanitizeIdCard("11010119900101123X"));
    }

    @Test
    @DisplayName("身份证脱敏 - null 返回 ***")
    void sanitizeIdCard_Null() {
        assertEquals("***", sanitizeService.sanitizeIdCard(null));
    }

    // endregion

    // region 密码脱敏

    @Test
    @DisplayName("密码脱敏 - 任意值替换为 ***")
    void sanitizePassword_AnyValue() {
        assertEquals("***", sanitizeService.sanitizePassword("p@ssw0rd"));
        assertEquals("***", sanitizeService.sanitizePassword(""));
        assertEquals("***", sanitizeService.sanitizePassword(null));
    }

    // endregion

    // region 手机号脱敏

    @Test
    @DisplayName("手机号脱敏 - 11 位前 3 后 4")
    void sanitizePhone_Standard() {
        assertEquals("138****1234", sanitizeService.sanitizePhone("13812341234"));
    }

    @Test
    @DisplayName("手机号脱敏 - null 返回 ***")
    void sanitizePhone_Null() {
        assertEquals("***", sanitizeService.sanitizePhone(null));
    }

    @Test
    @DisplayName("手机号脱敏 - 长度不足 8 返回 ***")
    void sanitizePhone_TooShort() {
        assertEquals("***", sanitizeService.sanitizePhone("12345"));
    }

    // endregion

    // region 邮箱脱敏

    @Test
    @DisplayName("邮箱脱敏 - 首字符 + *** + 域名")
    void sanitizeEmail_Standard() {
        assertEquals("a***@example.com", sanitizeService.sanitizeEmail("abc@example.com"));
    }

    @Test
    @DisplayName("邮箱脱敏 - 单字符用户名")
    void sanitizeEmail_SingleCharName() {
        assertEquals("a***@x.com", sanitizeService.sanitizeEmail("a@x.com"));
    }

    @Test
    @DisplayName("邮箱脱敏 - null 返回 null")
    void sanitizeEmail_Null() {
        assertNull(sanitizeService.sanitizeEmail(null));
    }

    @Test
    @DisplayName("邮箱脱敏 - 非法格式返回 ***")
    void sanitizeEmail_InvalidFormat() {
        assertEquals("***", sanitizeService.sanitizeEmail("not-an-email"));
    }

    // endregion

    // region action_params JSON 脱敏

    @Test
    @DisplayName("actionParams 脱敏 - null 原样返回")
    void sanitizeActionParams_Null() {
        assertNull(sanitizeService.sanitizeActionParams(null));
    }

    @Test
    @DisplayName("actionParams 脱敏 - 空白原样返回")
    void sanitizeActionParams_Blank() {
        assertEquals("   ", sanitizeService.sanitizeActionParams("   "));
    }

    @Test
    @DisplayName("actionParams 脱敏 - 密码字段名替换为 ***")
    void sanitizeActionParams_PasswordField() {
        String json = "{\"username\":\"admin\",\"password\":\"secret123\"}";
        String result = sanitizeService.sanitizeActionParams(json);
        // 密码字段被替换为 ***
        assertTrue(result.contains("\"password\":\"***\""), "密码应被替换为 ***");
        // 用户名保留
        assertTrue(result.contains("\"username\":\"admin\""), "用户名应保留");
    }

    @Test
    @DisplayName("actionParams 脱敏 - pwd 字段名替换为 ***")
    void sanitizeActionParams_PwdField() {
        String json = "{\"pwd\":\"abc\"}";
        String result = sanitizeService.sanitizeActionParams(json);
        assertTrue(result.contains("\"pwd\":\"***\""), "pwd 字段应被替换为 ***");
    }

    @Test
    @DisplayName("actionParams 脱敏 - 字符串值中银行卡脱敏")
    void sanitizeActionParams_CardInValue() {
        String json = "{\"card\":\"6225751212341234\"}";
        String result = sanitizeService.sanitizeActionParams(json);
        assertTrue(result.contains("6225********1234"), "卡号应被脱敏");
        assertFalse(result.contains("6225751212341234"), "原始卡号不应残留");
    }

    @Test
    @DisplayName("actionParams 脱敏 - 字符串值中手机号脱敏")
    void sanitizeActionParams_PhoneInValue() {
        String json = "{\"phone\":\"13812341234\"}";
        String result = sanitizeService.sanitizeActionParams(json);
        assertTrue(result.contains("138****1234"), "手机号应被脱敏");
    }

    @Test
    @DisplayName("actionParams 脱敏 - 字符串值中邮箱脱敏")
    void sanitizeActionParams_EmailInValue() {
        String json = "{\"email\":\"abc@example.com\"}";
        String result = sanitizeService.sanitizeActionParams(json);
        assertTrue(result.contains("a***@example.com"), "邮箱应被脱敏");
    }

    @Test
    @DisplayName("actionParams 脱敏 - 字符串值中身份证脱敏")
    void sanitizeActionParams_IdCardInValue() {
        String json = "{\"id\":\"110101199001011234\"}";
        String result = sanitizeService.sanitizeActionParams(json);
        assertTrue(result.contains("110101********1234"), "身份证应被脱敏");
    }

    @Test
    @DisplayName("actionParams 脱敏 - 嵌套对象递归脱敏")
    void sanitizeActionParams_NestedObject() {
        String json = "{\"user\":{\"password\":\"pw\",\"phone\":\"13812341234\"}}";
        String result = sanitizeService.sanitizeActionParams(json);
        assertTrue(result.contains("\"password\":\"***\""), "嵌套密码应脱敏");
        assertTrue(result.contains("138****1234"), "嵌套手机号应脱敏");
    }

    @Test
    @DisplayName("actionParams 脱敏 - 数组递归脱敏")
    void sanitizeActionParams_Array() {
        String json = "{\"contacts\":[\"13812341234\",\"13912341234\"]}";
        String result = sanitizeService.sanitizeActionParams(json);
        assertTrue(result.contains("138****1234"), "数组中手机号应脱敏");
        assertTrue(result.contains("139****1234"), "数组中第二个手机号应脱敏");
    }

    @Test
    @DisplayName("actionParams 脱敏 - 数字/布尔值原样保留")
    void sanitizeActionParams_NumberAndBoolean() {
        String json = "{\"amount\":50000,\"enabled\":true}";
        String result = sanitizeService.sanitizeActionParams(json);
        assertTrue(result.contains("\"amount\":50000"), "数字应保留");
        assertTrue(result.contains("\"enabled\":true"), "布尔值应保留");
    }

    @Test
    @DisplayName("actionParams 脱敏 - 非法 JSON 降级正则脱敏")
    void sanitizeActionParams_InvalidJson_Fallback() {
        String raw = "contact me at 13812341234 or abc@example.com";
        String result = sanitizeService.sanitizeActionParams(raw);
        assertTrue(result.contains("138****1234"), "降级后手机号应脱敏");
        assertTrue(result.contains("a***@example.com"), "降级后邮箱应脱敏");
    }

    @Test
    @DisplayName("actionParams 脱敏 - 非敏感纯文本不变")
    void sanitizeActionParams_PlainText_NoSensitive() {
        String json = "{\"action\":\"click\",\"target\":\"登录按钮\"}";
        String result = sanitizeService.sanitizeActionParams(json);
        assertTrue(result.contains("\"action\":\"click\""), "非敏感字段应保留");
        assertTrue(result.contains("\"target\":\"登录按钮\""), "中文非敏感字段应保留");
    }

    // endregion
}
