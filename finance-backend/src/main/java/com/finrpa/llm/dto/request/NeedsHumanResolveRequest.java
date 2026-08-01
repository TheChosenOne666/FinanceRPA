package com.finrpa.llm.dto.request;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * NEEDS_HUMAN 处置请求 DTO（操作员 → Java）
 *
 * <p>操作员查看 NEEDS_HUMAN 事件详情后，选择处置动作：
 * <ul>
 *   <li>skip —— 跳过当前子任务，续跑任务</li>
 *   <li>manual —— 人工已处理，续跑任务</li>
 *   <li>abort —— 终止任务</li>
 * </ul>
 * </p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
public class NeedsHumanResolveRequest implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 处置动作：skip / manual / abort */
    private String action;
}
