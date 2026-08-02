package com.finrpa.audit.constant;

/**
 * 审计模块常量
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
public interface AuditConstant {

    /** 执行结果：成功 */
    String RESULT_SUCCESS = "success";

    /** 执行结果：失败 */
    String RESULT_FAILED = "failed";

    /** 密码类字段名匹配正则（不区分大小写，匹配 password / pwd / passwd / secret） */
    String PASSWORD_FIELD_REGEX = "(?i).*(password|pwd|passwd|secret).*";

    /** 银行卡号正则（13-19 位连续数字） */
    String CARD_REGEX = "\\d{13,19}";

    /** 身份证号正则（18 位，末位可为 X） */
    String ID_CARD_REGEX = "\\d{17}[0-9Xx]";

    /** 手机号正则（11 位数字，国内号段） */
    String PHONE_REGEX = "1[3-9]\\d{9}";

    /** 邮箱正则 */
    String EMAIL_REGEX = "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}";
}
