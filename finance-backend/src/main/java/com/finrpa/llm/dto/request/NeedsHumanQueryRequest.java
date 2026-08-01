package com.finrpa.llm.dto.request;

import com.finrpa.common.model.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * NEEDS_HUMAN 队列查询请求 DTO
 *
 * <p>支持按状态、任务 ID 筛选，继承 {@link PageRequest} 提供分页能力。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class NeedsHumanQueryRequest extends PageRequest {

    /** 队列状态筛选（PENDING / RESOLVED，可空表示全部） */
    private String status;

    /** 任务 ID 筛选（可空） */
    private Long taskId;
}
