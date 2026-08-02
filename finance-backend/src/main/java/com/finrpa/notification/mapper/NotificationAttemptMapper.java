package com.finrpa.notification.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.finrpa.notification.entity.NotificationAttemptEO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 通知发送尝试记录 Mapper（M6.6）
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Mapper
public interface NotificationAttemptMapper extends BaseMapper<NotificationAttemptEO> {
}
