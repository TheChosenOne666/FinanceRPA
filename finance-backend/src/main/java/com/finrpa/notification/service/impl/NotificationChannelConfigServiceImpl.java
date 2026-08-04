package com.finrpa.notification.service.impl;

import com.finrpa.common.exception.ThrowUtils;
import com.finrpa.common.response.ErrorCode;
import com.finrpa.notification.constant.NotificationConstant;
import com.finrpa.notification.config.NotificationProperties;
import com.finrpa.notification.dto.request.ChannelConfigSaveRequest;
import com.finrpa.notification.entity.NotificationChannelConfigEO;
import com.finrpa.notification.enums.NotificationChannelEnum;
import com.finrpa.notification.mapper.NotificationChannelConfigMapper;
import com.finrpa.notification.service.NotificationChannelConfigService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 通知通道 Webhook 配置服务实现（P0-4）
 *
 * <p>核心职责：
 * <ul>
 *   <li>{@link #init()} —— 应用启动时从数据库加载配置，覆盖 {@link NotificationProperties} 内存值</li>
 *   <li>{@link #saveConfig(String, ChannelConfigSaveRequest)} —— 保存配置到数据库 + 同步更新内存</li>
 *   <li>{@link #isChannelEnabled(String)} —— 判断通道是否启用（数据库 enabled=1 且 webhookUrl 非空）</li>
 * </ul>
 * </p>
 *
 * <p><b>热生效机制</b>：保存配置后直接修改 {@code NotificationProperties} 单例的内存值，
 * 通道实现类（{@code WeComChannel} / {@code DingTalkChannel}）下次读取即生效。
 * 单实例部署下无需广播；多实例部署需扩展为 Redis Pub/Sub 通知。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Slf4j
@Service
public class NotificationChannelConfigServiceImpl implements NotificationChannelConfigService {

    /** 通知模块配置（运行时可写入） */
    @Resource
    private NotificationProperties properties;

    /** 通道配置 Mapper */
    @Resource
    private NotificationChannelConfigMapper mapper;

    // region 初始化

    /**
     * 应用启动时从数据库加载配置覆盖 NotificationProperties 内存值
     *
     * <p>数据库为空时回退到 yml 环境变量初始值，并将初始值写入数据库。</p>
     */
    @PostConstruct
    @Override
    public void init() {
        // 1. 遍历所有通道枚举，从数据库加载配置
        for (NotificationChannelEnum channelEnum : NotificationChannelEnum.values()) {
            String channel = channelEnum.getValue();
            NotificationChannelConfigEO config = mapper.selectById(channel);
            if (config == null) {
                // 2. 数据库无记录：用 yml 初始值插入
                config = buildConfigFromProperties(channel);
                mapper.insert(config);
                log.info("通知通道 {} 初始化（yml 默认值）: configured={}", channel,
                        !config.getWebhookUrl().isBlank());
            } else {
                // 3. 数据库有记录：用数据库值覆盖内存
                applyConfigToProperties(config);
                log.info("通知通道 {} 加载（数据库）: configured={}, enabled={}", channel,
                        !config.getWebhookUrl().isBlank(), config.getEnabled() == 1);
            }
        }
    }

    // endregion

    // region 查询

    /**
     * 查询所有通道配置
     *
     * @return 通道配置列表
     */
    @Override
    public List<NotificationChannelConfigEO> listAll() {
        return mapper.selectList(null);
    }

    /**
     * 按通道类型查询配置
     *
     * @param channel 通道类型：wecom / dingtalk
     * @return 通道配置；不存在返回 null
     */
    @Override
    public NotificationChannelConfigEO getByChannel(String channel) {
        return mapper.selectById(channel);
    }

    // endregion

    // region 保存

    /**
     * 保存通道 Webhook 配置（持久化 + 热更新内存）
     *
     * @param channel 通道类型：wecom / dingtalk
     * @param request 保存请求（webhookUrl / secret / enabled）
     * @return 保存后的配置实体
     */
    @Override
    public NotificationChannelConfigEO saveConfig(String channel, ChannelConfigSaveRequest request) {
        // 1. 校验通道类型
        NotificationChannelEnum channelEnum = NotificationChannelEnum.getEnumByValue(channel);
        ThrowUtils.throwIf(channelEnum == null, ErrorCode.PARAMS_ERROR, "无效的通道类型: " + channel);
        ThrowUtils.throwIf(request.getEnabled() == null, ErrorCode.PARAMS_ERROR, "启用状态不能为空");

        // 2. 查询已有记录
        NotificationChannelConfigEO existing = mapper.selectById(channel);
        if (existing == null) {
            // 3. 不存在则新增
            NotificationChannelConfigEO newConfig = new NotificationChannelConfigEO();
            newConfig.setChannel(channel);
            newConfig.setWebhookUrl(request.getWebhookUrl() == null ? "" : request.getWebhookUrl());
            newConfig.setSecret(request.getSecret() == null ? "" : request.getSecret());
            newConfig.setEnabled(request.getEnabled() ? 1 : 0);
            mapper.insert(newConfig);
            log.info("通知通道 {} 新增 Webhook 配置: enabled={}", channel, request.getEnabled());
            // 4. 同步内存
            applyConfigToProperties(newConfig);
            return newConfig;
        }

        // 5. 已存在则更新
        existing.setWebhookUrl(request.getWebhookUrl() == null ? "" : request.getWebhookUrl());
        existing.setSecret(request.getSecret() == null ? "" : request.getSecret());
        existing.setEnabled(request.getEnabled() ? 1 : 0);
        mapper.updateById(existing);
        log.info("通知通道 {} 更新 Webhook 配置: enabled={}", channel, request.getEnabled());
        // 6. 同步内存
        applyConfigToProperties(existing);
        return existing;
    }

    // endregion

    // region 判断启用

    /**
     * 判断通道是否启用（数据库 enabled=1 且 webhookUrl 非空）
     *
     * @param channel 通道类型
     * @return true=启用 / false=禁用或未配置
     */
    @Override
    public boolean isChannelEnabled(String channel) {
        NotificationChannelConfigEO config = mapper.selectById(channel);
        if (config == null) {
            return false;
        }
        return config.getEnabled() == 1
                && config.getWebhookUrl() != null
                && !config.getWebhookUrl().isBlank();
    }

    // endregion

    // region 私有方法

    /**
     * 将数据库配置同步到 NotificationProperties 内存（热生效）
     *
     * @param config 数据库配置
     */
    private void applyConfigToProperties(NotificationChannelConfigEO config) {
        String channel = config.getChannel();
        String webhookUrl = config.getWebhookUrl() == null ? "" : config.getWebhookUrl();
        String secret = config.getSecret() == null ? "" : config.getSecret();

        if (NotificationConstant.CHANNEL_WECOM.equals(channel)) {
            properties.getWecom().setWebhookUrl(webhookUrl);
        } else if (NotificationConstant.CHANNEL_DINGTALK.equals(channel)) {
            properties.getDingtalk().setWebhookUrl(webhookUrl);
            properties.getDingtalk().setSecret(secret);
        }
    }

    /**
     * 从 NotificationProperties 当前内存值构建数据库实体（首次初始化用）
     *
     * @param channel 通道类型
     * @return 配置实体
     */
    private NotificationChannelConfigEO buildConfigFromProperties(String channel) {
        NotificationChannelConfigEO config = new NotificationChannelConfigEO();
        config.setChannel(channel);
        config.setEnabled(1);

        if (NotificationConstant.CHANNEL_WECOM.equals(channel)) {
            String url = properties.getWecom().getWebhookUrl();
            config.setWebhookUrl(url == null ? "" : url);
            config.setSecret("");
        } else if (NotificationConstant.CHANNEL_DINGTALK.equals(channel)) {
            String url = properties.getDingtalk().getWebhookUrl();
            String secret = properties.getDingtalk().getSecret();
            config.setWebhookUrl(url == null ? "" : url);
            config.setSecret(secret == null ? "" : secret);
        } else {
            config.setWebhookUrl("");
            config.setSecret("");
        }
        return config;
    }

    // endregion
}
