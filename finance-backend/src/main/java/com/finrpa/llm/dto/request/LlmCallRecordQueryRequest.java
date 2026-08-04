package com.finrpa.llm.dto.request;

import com.finrpa.common.model.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.sql.Timestamp;

/**
 * LLM 调用记录分页查询请求 DTO（P3 ai-monitoring 原型对齐）
 *
 * <p>用于前端 LLM 监控页底部"调用记录"列表的分页查询，继承 {@link PageRequest} 提供分页能力。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class LlmCallRecordQueryRequest extends PageRequest {

    /** 序列化版本号 */
    private static final long serialVersionUID = 1L;

    /** 起始时间（按 call_time 过滤，可空） */
    private Timestamp startTime;

    /** 结束时间（按 call_time 过滤，可空） */
    private Timestamp endTime;

    /** 模型名筛选（可空，精确匹配） */
    private String model;

    /** 任务 ID 筛选（可空） */
    private Long taskId;

    /** 业务线 ID 筛选（可空） */
    private Long businessLineId;

    /** 是否仅查询缓存命中记录（可空：true 仅缓存命中 / false 仅未命中 / null 全部） */
    private Boolean cacheHit;
}
