package com.finrpa.notification.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

/**
 * 通知测试发送请求（M6.6）
 *
 * <p>对应 POST /api/v1/notification/test 接口入参：
 * 选择通道 + 模板类型 + 自定义参数，触发一次测试发送。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
public class NotificationTestRequest implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 通道类型：wecom / dingtalk（必填） */
    @NotBlank(message = "通道类型不能为空")
    private String channel;

    /** 模板类型：APPROVAL_PENDING / APPROVAL_TIMEOUT / TASK_FAILED / NEEDS_HUMAN / RISK_ESCALATION（必填） */
    @NotBlank(message = "模板类型不能为空")
    private String templateType;

    /** 模板参数（键值对，可选；不同模板支持不同参数） */
    private Map<String, Object> params;
}
