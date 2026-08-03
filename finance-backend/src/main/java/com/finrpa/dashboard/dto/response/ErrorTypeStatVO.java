package com.finrpa.dashboard.dto.response;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 错误类型统计 VO（对齐系统设计 6.9.1 错误：错误类型分布 Top 10）
 *
 * <p>按失败操作类型（audit_log.action_type where execution_result='failed'）聚合统计。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
public class ErrorTypeStatVO implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 错误类型（失败操作类型，如 CLICK / INPUT_TEXT / LOGIN 等） */
    private String errorType;

    /** 出现次数 */
    private Long count;
}
