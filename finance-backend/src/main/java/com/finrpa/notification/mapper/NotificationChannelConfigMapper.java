package com.finrpa.notification.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.finrpa.notification.entity.NotificationChannelConfigEO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 通知通道 Webhook 配置 Mapper（P0-4）
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Mapper
public interface NotificationChannelConfigMapper extends BaseMapper<NotificationChannelConfigEO> {
}
