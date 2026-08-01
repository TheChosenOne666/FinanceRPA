package com.finrpa.approval.dto.request;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

/**
 * 风险预筛请求 DTO
 *
 * <p>对任务目标 + 参数进行关键词预筛与金额检测。
 * M6.1 阶段仅做关键词匹配 + 金额正则检测，不调 LLM。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
public class RiskDetectRequest implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 任务目标（如"下载银行流水"） */
    private String goal;

    /** 任务参数（业务自定义键值对，会拼接成文本进行匹配） */
    private Map<String, Object> params;

    /** 所属行业：banking / insurance / securities（可空，为空时全行业匹配） */
    private String industry;

    /** 任务 ID（可空，用于日志追踪） */
    private Long taskId;
}
