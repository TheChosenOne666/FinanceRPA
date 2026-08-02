package com.finrpa.notification.constant;

/**
 * 通知模块常量（M6.6）
 *
 * <p>定义通知通道、模板类型、钉钉加签算法等常量。
 * 通道实现：企业微信群机器人 + 钉钉群机器人 Webhook；
 * 模板覆盖审批待处理 / 审批超时 / 任务失败 / NEEDS_HUMAN 接管 / 风险等级升级 五类场景。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
public interface NotificationConstant {

    // region 通知通道（与 NotificationChannelEnum.getValue 对齐）

    /** 企业微信群机器人 */
    String CHANNEL_WECOM = "wecom";

    /** 钉钉群机器人 */
    String CHANNEL_DINGTALK = "dingtalk";

    // endregion

    // region 通知模板类型（与 NotificationTemplateEnum.getValue 对齐）

    /** 模板类型：审批待处理（approval:requests 发布时触发） */
    String TEMPLATE_APPROVAL_PENDING = "APPROVAL_PENDING";

    /** 模板类型：审批超时告警（超时检测触发时） */
    String TEMPLATE_APPROVAL_TIMEOUT = "APPROVAL_TIMEOUT";

    /** 模板类型：任务失败（任务终态为 failed 时） */
    String TEMPLATE_TASK_FAILED = "TASK_FAILED";

    /** 模板类型：NEEDS_HUMAN 接管（LLM 三层容错失败时） */
    String TEMPLATE_NEEDS_HUMAN = "NEEDS_HUMAN";

    /** 模板类型：风险等级升级（LLM 判断升级 risk_level 时） */
    String TEMPLATE_RISK_ESCALATION = "RISK_ESCALATION";

    // endregion

    // region 通道请求 Content-Type

    /** 通道请求 Content-Type：application/json */
    String CONTENT_TYPE_JSON = "application/json";

    // endregion

    // region 钉钉加签常量

    /** 钉钉加签算法：HmacSHA256 */
    String DINGTALK_SIGN_ALGORITHM = "HmacSHA256";

    /** 钉钉加签编码：UTF-8 */
    String DINGTALK_CHARSET = "UTF-8";

    // endregion

    // region Redis 重试队列（M6.6 扩展 system-design.md 6.10.3）

    /** Redis List Key：通知重试队列（LPUSH 入队 / RPOP 出队，FIFO） */
    String RETRY_QUEUE_KEY = "notification:retry_queue";

    /** 重试调度器 cron 表达式：每 5 分钟执行一次 */
    String RETRY_SCHEDULER_CRON = "0 */5 * * * ?";

    /** ShedLock 锁名称：通知重试调度（分布式部署下保证仅单节点执行） */
    String RETRY_SCHEDULER_LOCK_NAME = "notification:retry:scheduler";

    /** 最大重试次数（含首次发送）：超过则告警人工介入，不再重试 */
    int MAX_RETRY_COUNT = 3;

    // endregion
}
