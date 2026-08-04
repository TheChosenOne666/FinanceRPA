package com.finrpa.notification.service;

import com.finrpa.notification.dto.request.ChannelConfigSaveRequest;
import com.finrpa.notification.entity.NotificationChannelConfigEO;

import java.util.List;

/**
 * 通知通道 Webhook 配置服务接口（P0-4）
 *
 * <p>管理企业微信 / 钉钉群机器人 Webhook URL、加签密钥、启用状态的持久化与运行时热生效。
 * 保存配置后同步更新 {@code NotificationProperties} 内存值，通道发送时立即生效。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
public interface NotificationChannelConfigService {

    /**
     * 应用启动时从数据库加载配置覆盖 NotificationProperties 内存值
     *
     * <p>由 {@code @PostConstruct} 触发，确保运行时内存值与数据库一致。
     * 数据库为空时回退到 yml 环境变量初始值。</p>
     */
    void init();

    /**
     * 查询所有通道配置（含未配置的通道）
     *
     * @return 通道配置列表
     */
    List<NotificationChannelConfigEO> listAll();

    /**
     * 按通道类型查询配置
     *
     * @param channel 通道类型：wecom / dingtalk
     * @return 通道配置；不存在返回 null
     */
    NotificationChannelConfigEO getByChannel(String channel);

    /**
     * 保存通道 Webhook 配置（持久化 + 热更新内存）
     *
     * @param channel 通道类型：wecom / dingtalk
     * @param request 保存请求（webhookUrl / secret / enabled）
     * @return 保存后的配置实体
     */
    NotificationChannelConfigEO saveConfig(String channel, ChannelConfigSaveRequest request);

    /**
     * 判断通道是否启用（数据库 enabled=1 且 webhookUrl 非空）
     *
     * @param channel 通道类型
     * @return true=启用 / false=禁用或未配置
     */
    boolean isChannelEnabled(String channel);
}
