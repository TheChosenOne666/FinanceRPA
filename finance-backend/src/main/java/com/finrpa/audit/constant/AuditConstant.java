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

    // region 截图存储（M7.2 MinIO）

    /** 截图阶段：操作前 */
    String SCREENSHOT_PHASE_BEFORE = "before";

    /** 截图阶段：操作后 */
    String SCREENSHOT_PHASE_AFTER = "after";

    /** 截图文件扩展名 */
    String SCREENSHOT_FILE_EXTENSION = ".png";

    /** 截图对象路径分隔符 */
    String SCREENSHOT_PATH_SEPARATOR = "/";

    /** 截图清理调度器 cron 表达式：每日凌晨 3 点执行 */
    String SCREENSHOT_CLEANUP_CRON = "0 0 3 * * ?";

    /** 截图清理调度器 ShedLock 锁名称 */
    String SCREENSHOT_CLEANUP_LOCK_NAME = "audit-screenshot-cleanup";

    /** 截图清理调度器锁最大持有时间（30 分钟，清理大量对象时可能较慢） */
    String SCREENSHOT_CLEANUP_LOCK_AT_MOST = "PT30M";

    /** 截图清理调度器锁最短持有时间（10 秒，避免瞬间释放导致重复执行） */
    String SCREENSHOT_CLEANUP_LOCK_AT_LEAST = "PT10S";

    // endregion
}
