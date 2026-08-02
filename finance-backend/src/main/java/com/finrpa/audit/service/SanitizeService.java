package com.finrpa.audit.service;

/**
 * 审计日志脱敏服务（系统设计 6.4.3）
 *
 * <p>提供银行卡 / 身份证 / 密码 / 手机 / 邮箱 五类敏感字段脱敏规则，
 * 以及对 action_params JSON 的整体脱敏能力。Python 上报的原始参数在持久化前必须经此服务脱敏。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
public interface SanitizeService {

    /**
     * 银行卡号脱敏：前 4 后 4，中间用 * 填充
     *
     * @param cardNo 银行卡号
     * @return 脱敏后的卡号，如 6225****1234
     */
    String sanitizeCard(String cardNo);

    /**
     * 身份证号脱敏：前 6 后 4，中间用 * 填充
     *
     * @param idCard 身份证号
     * @return 脱敏后的身份证号，如 110101********1234
     */
    String sanitizeIdCard(String idCard);

    /**
     * 密码脱敏：完全替换为 ***
     *
     * @param password 原始密码
     * @return 固定返回 ***
     */
    String sanitizePassword(String password);

    /**
     * 手机号脱敏：前 3 后 4，中间用 * 填充
     *
     * @param phone 手机号
     * @return 脱敏后的手机号，如 138****1234
     */
    String sanitizePhone(String phone);

    /**
     * 邮箱脱敏：首字符 + *** + @域名
     *
     * @param email 邮箱
     * @return 脱敏后的邮箱，如 a***@example.com
     */
    String sanitizeEmail(String email);

    /**
     * 对 action_params JSON 进行整体脱敏
     *
     * <p>规则：
     * <ol>
     *   <li>字段名匹配 password/pwd/passwd/secret（不区分大小写）→ 整值替换为 ***</li>
     *   <li>其余字符串值用正则识别银行卡 / 身份证 / 手机 / 邮箱并脱敏</li>
     *   <li>递归处理嵌套对象与数组</li>
     *   <li>非合法 JSON 时降级为对原始字符串做正则脱敏</li>
     * </ol>
     * </p>
     *
     * @param json 原始 action_params JSON
     * @return 脱敏后的 JSON 字符串；入参为 null/空白时原样返回
     */
    String sanitizeActionParams(String json);
}
