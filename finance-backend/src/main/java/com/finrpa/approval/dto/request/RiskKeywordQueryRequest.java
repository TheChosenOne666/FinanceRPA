package com.finrpa.approval.dto.request;

import com.finrpa.common.model.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 风险关键词查询请求 DTO
 *
 * <p>支持按行业、分类、启用状态筛选关键词库。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class RiskKeywordQueryRequest extends PageRequest {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 关键词模糊匹配（可空） */
    private String keyword;

    /** 所属行业：banking / insurance / securities（可空） */
    private String industry;

    /** 分类：high_risk_operation / sensitive_data / large_amount（可空） */
    private String category;

    /** 风险类型：high / medium / low（可空） */
    private String riskType;

    /** 启用状态：0-禁用 1-启用（可空，默认全部） */
    private Integer enabled;
}
