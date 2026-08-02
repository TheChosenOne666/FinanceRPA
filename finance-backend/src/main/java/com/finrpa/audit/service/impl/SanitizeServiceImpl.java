package com.finrpa.audit.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.finrpa.audit.constant.AuditConstant;
import com.finrpa.audit.service.SanitizeService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 审计日志脱敏服务实现（系统设计 6.4.3）
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Slf4j
@Service
public class SanitizeServiceImpl implements SanitizeService {

    /** JSON 序列化工具 */
    @Resource
    private ObjectMapper objectMapper;

    /** 密码字段名正则（预编译） */
    private static final Pattern PASSWORD_FIELD_PATTERN = Pattern.compile(AuditConstant.PASSWORD_FIELD_REGEX);

    /** 身份证号正则（预编译） */
    private static final Pattern ID_CARD_PATTERN = Pattern.compile(AuditConstant.ID_CARD_REGEX);

    /** 银行卡号正则（预编译） */
    private static final Pattern CARD_PATTERN = Pattern.compile(AuditConstant.CARD_REGEX);

    /** 手机号正则（预编译） */
    private static final Pattern PHONE_PATTERN = Pattern.compile(AuditConstant.PHONE_REGEX);

    /** 邮箱正则（预编译） */
    private static final Pattern EMAIL_PATTERN = Pattern.compile(AuditConstant.EMAIL_REGEX);

    // region 单字段脱敏

    /**
     * 银行卡号脱敏：前 4 后 4，中间用 * 填充
     *
     * @param cardNo 银行卡号
     * @return 脱敏后的卡号
     */
    @Override
    public String sanitizeCard(String cardNo) {
        if (cardNo == null || cardNo.length() < 9) {
            // 长度不足无法保留前4后4，整体替换为 ***
            return "***";
        }
        int len = cardNo.length();
        String head = cardNo.substring(0, 4);
        String tail = cardNo.substring(len - 4);
        return head + "*".repeat(len - 8) + tail;
    }

    /**
     * 身份证号脱敏：前 6 后 4，中间用 * 填充
     *
     * @param idCard 身份证号
     * @return 脱敏后的身份证号
     */
    @Override
    public String sanitizeIdCard(String idCard) {
        if (idCard == null || idCard.length() < 11) {
            return "***";
        }
        int len = idCard.length();
        String head = idCard.substring(0, 6);
        String tail = idCard.substring(len - 4);
        return head + "*".repeat(len - 10) + tail;
    }

    /**
     * 密码脱敏：完全替换为 ***
     *
     * @param password 原始密码
     * @return 固定返回 ***
     */
    @Override
    public String sanitizePassword(String password) {
        return "***";
    }

    /**
     * 手机号脱敏：前 3 后 4，中间用 * 填充
     *
     * @param phone 手机号
     * @return 脱敏后的手机号
     */
    @Override
    public String sanitizePhone(String phone) {
        if (phone == null || phone.length() < 8) {
            return "***";
        }
        int len = phone.length();
        String head = phone.substring(0, 3);
        String tail = phone.substring(len - 4);
        return head + "*".repeat(len - 7) + tail;
    }

    /**
     * 邮箱脱敏：首字符 + *** + @域名
     *
     * @param email 邮箱
     * @return 脱敏后的邮箱
     */
    @Override
    public String sanitizeEmail(String email) {
        if (email == null) {
            return null;
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            // 非法邮箱格式，整体脱敏
            return "***";
        }
        String name = email.substring(0, at);
        String domain = email.substring(at);
        if (name.length() <= 1) {
            return name + "***" + domain;
        }
        return name.charAt(0) + "***" + domain;
    }

    // endregion

    // region action_params JSON 脱敏

    /**
     * 对 action_params JSON 进行整体脱敏
     *
     * @param json 原始 action_params JSON
     * @return 脱敏后的 JSON 字符串
     */
    @Override
    public String sanitizeActionParams(String json) {
        // 1. null/空白原样返回
        if (json == null || json.isBlank()) {
            return json;
        }

        try {
            // 2. 解析 JSON 并递归脱敏
            JsonNode root = objectMapper.readTree(json);
            JsonNode sanitized = sanitizeNode(root);
            return objectMapper.writeValueAsString(sanitized);
        } catch (Exception e) {
            // 3. JSON 解析失败，降级为对原始字符串做正则脱敏
            log.warn("action_params JSON 解析失败，降级为正则脱敏: {}", e.getMessage());
            return sanitizeStringValue(json);
        }
    }

    /**
     * 递归脱敏 JsonNode
     *
     * @param node 待脱敏节点
     * @return 脱敏后的节点（原节点不被修改）
     */
    private JsonNode sanitizeNode(JsonNode node) {
        if (node == null || node.isNull()) {
            return node;
        }
        // 1. 对象节点：遍历字段，按字段名 + 值类型脱敏
        if (node.isObject()) {
            ObjectNode obj = (ObjectNode) node;
            for (var field = obj.fields(); field.hasNext(); ) {
                var entry = field.next();
                String fieldName = entry.getKey();
                JsonNode value = entry.getValue();
                // 字段名匹配密码类 → 整值替换为 ***
                if (PASSWORD_FIELD_PATTERN.matcher(fieldName).matches() && value.isTextual()) {
                    obj.set(fieldName, TextNode.valueOf("***"));
                } else {
                    obj.set(fieldName, sanitizeNode(value));
                }
            }
            return obj;
        }
        // 2. 数组节点：逐元素递归
        if (node.isArray()) {
            ArrayNode arr = (ArrayNode) node;
            for (int i = 0; i < arr.size(); i++) {
                arr.set(i, sanitizeNode(arr.get(i)));
            }
            return arr;
        }
        // 3. 文本节点：正则识别敏感信息并脱敏
        if (node.isTextual()) {
            return TextNode.valueOf(sanitizeStringValue(node.asText()));
        }
        // 4. 其他类型（数字/布尔）原样返回
        return node;
    }

    /**
     * 对字符串值依次执行身份证 / 银行卡 / 手机 / 邮箱正则脱敏
     *
     * <p>顺序：先身份证（18位含X）再银行卡（13-19位纯数字），
     * 避免 18 位纯数字身份证被银行卡正则先行匹配导致规则错配。</p>
     *
     * @param value 原始字符串
     * @return 脱敏后的字符串
     */
    private String sanitizeStringValue(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        String result = value;
        result = replaceAll(result, ID_CARD_PATTERN, this::sanitizeIdCard);
        result = replaceAll(result, CARD_PATTERN, this::sanitizeCard);
        result = replaceAll(result, PHONE_PATTERN, this::sanitizePhone);
        result = replaceAll(result, EMAIL_PATTERN, this::sanitizeEmail);
        return result;
    }

    /**
     * 用正则匹配并逐处替换
     *
     * @param input    原始字符串
     * @param pattern  正则
     * @param replacer 替换函数（入参为匹配到的子串）
     * @return 替换后的字符串
     */
    private String replaceAll(String input, Pattern pattern, Function<String, String> replacer) {
        Matcher m = pattern.matcher(input);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement(replacer.apply(m.group())));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    // endregion
}
