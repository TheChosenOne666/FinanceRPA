package com.finrpa.llm.dto.request;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.sql.Timestamp;

/**
 * LLM 调用统计查询请求 DTO
 *
 * <p>支持按时间范围、模型、任务维度筛选调用记录进行统计。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
public class LlmCallStatsQueryRequest implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 起始时间（按 create_time 过滤，可空） */
    private Timestamp startTime;

    /** 结束时间（按 create_time 过滤，可空） */
    private Timestamp endTime;

    /** 模型名筛选（可空，精确匹配） */
    private String model;

    /** 任务 ID 筛选（可空） */
    private Long taskId;
}
