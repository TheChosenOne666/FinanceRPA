package com.finrpa.notification.service;

import com.finrpa.notification.dto.request.NotificationTestRequest;
import com.finrpa.notification.dto.response.ChannelVO;
import com.finrpa.notification.dto.response.NotificationSendResultVO;
import com.finrpa.notification.dto.response.RetryQueueStatsVO;
import com.finrpa.notification.enums.NotificationChannelEnum;
import com.finrpa.notification.enums.NotificationTemplateEnum;

import java.util.List;
import java.util.Map;

/**
 * 通知服务接口（M6.6）
 *
 * <p>统一封装通知发送、通道查询、测试发送、重试队列查询能力。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
public interface NotificationService {

    /**
     * 查询所有通道及其配置状态
     *
     * @return 通道信息列表（含企业微信 / 钉钉）
     */
    List<ChannelVO> listChannels();

    /**
     * 按通道 + 模板类型 + 参数发送通知（单通道直发，测试 API 使用）
     *
     * @param channel      通道枚举
     * @param template     模板枚举
     * @param params       模板参数（可为 null）
     * @return 发送结果
     */
    NotificationSendResultVO send(NotificationChannelEnum channel, NotificationTemplateEnum template,
                                    Map<String, Object> params);

    /**
     * 测试发送（API 入口）
     *
     * @param request 测试发送请求
     * @return 发送结果
     */
    NotificationSendResultVO test(NotificationTestRequest request);

    /**
     * 调度发送通知（含 Fallback 机制 + 入重试队列）
     *
     * <p>业务流程入口（审批触发等）统一调用此方法。</p>
     *
     * @param template    模板枚举
     * @param params      模板参数
     * @param approvalId  关联审批单 ID（可空）
     * @param taskId      关联任务 ID（可空）
     * @param targetUserId 目标用户 ID（可空）
     * @return 是否最终发送成功（true=任一通道成功 / false=全部失败已入队）
     */
    boolean dispatch(NotificationTemplateEnum template, Map<String, Object> params,
                      Long approvalId, Long taskId, Long targetUserId);

    /**
     * 查询重试队列待处理任务数
     *
     * @return 待处理任务数
     */
    long getRetryQueueSize();

    /**
     * 查询重试队列统计
     *
     * @return 统计 VO（队列长度 + 总尝试次数 + 成功率 + 告警数）
     */
    RetryQueueStatsVO getRetryStats();
}

