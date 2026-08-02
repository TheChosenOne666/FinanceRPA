package com.finrpa.notification.controller;

import com.finrpa.common.response.BaseResponse;
import com.finrpa.common.response.ResultUtils;
import com.finrpa.notification.dto.request.NotificationTestRequest;
import com.finrpa.notification.dto.response.ChannelVO;
import com.finrpa.notification.dto.response.NotificationSendResultVO;
import com.finrpa.notification.dto.response.RetryQueueStatsVO;
import com.finrpa.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 通知管理控制器（对外 API）（M6.6）
 *
 * <p>对外端点（实际访问路径前缀 {@code /api/notification}）：
 * <ul>
 *   <li>GET /notification/channels —— 查询所有通道及其配置状态</li>
 *   <li>POST /notification/test —— 测试发送（指定通道 + 模板 + 参数）</li>
 *   <li>GET /notification/retry/queue —— 查询重试队列待处理任务数</li>
 *   <li>GET /notification/retry/stats —— 查询重试队列统计（成功率 / 失败次数 / 告警数）</li>
 * </ul>
 * </p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Slf4j
@RestController
@RequestMapping("/notification")
@Tag(name = "通知管理", description = "通知通道查询 + 测试发送 + 重试队列统计")
public class NotificationController {

    /** 通知服务 */
    @Resource
    private NotificationService notificationService;

    /**
     * 查询所有通知通道及其配置状态
     *
     * @return 通道列表
     */
    @GetMapping("/channels")
    @Operation(summary = "通道列表", description = "查询所有通知通道（企业微信 / 钉钉）及其 Webhook 配置状态")
    public BaseResponse<List<ChannelVO>> listChannels() {
        return ResultUtils.success(notificationService.listChannels());
    }

    /**
     * 测试发送通知
     *
     * @param request 测试请求（通道 + 模板类型 + 参数）
     * @return 发送结果
     */
    @PostMapping("/test")
    @Operation(summary = "测试发送", description = "按通道 + 模板类型 + 参数触发一次通知发送，用于验证 Webhook 配置")
    public BaseResponse<NotificationSendResultVO> test(@Valid @RequestBody NotificationTestRequest request) {
        return ResultUtils.success(notificationService.test(request));
    }

    /**
     * 查询重试队列待处理任务数
     *
     * @return 待处理任务数
     */
    @GetMapping("/retry/queue")
    @Operation(summary = "重试队列长度", description = "查询通知重试队列中待处理任务数（主通道 + Fallback 均失败的任务）")
    public BaseResponse<Long> getRetryQueueSize() {
        return ResultUtils.success(notificationService.getRetryQueueSize());
    }

    /**
     * 查询重试队列统计
     *
     * @return 统计 VO（队列长度 + 总尝试次数 + 成功率 + 失败次数 + 告警数）
     */
    @GetMapping("/retry/stats")
    @Operation(summary = "重试队列统计", description = "查询通知发送的整体统计：总尝试次数 / 成功率 / 失败次数 / 告警数")
    public BaseResponse<RetryQueueStatsVO> getRetryStats() {
        return ResultUtils.success(notificationService.getRetryStats());
    }
}

