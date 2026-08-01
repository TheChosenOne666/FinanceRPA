package com.finrpa.approval.dto.request;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

/**
 * 风险关键词新增 / 更新请求 DTO
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
public class RiskKeywordAddRequest implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 关键词文本 */
    private String keyword;

    /** 所属行业：banking / insurance / securities */
    private String industry;

    /** 分类：high_risk_operation / sensitive_data / large_amount */
    private String category;

    /** 风险类型：high / medium / low */
    private String riskType;

    /** 描述说明（可空） */
    private String description;

    /** 启用状态（默认 1-启用） */
    private Integer enabled;

    /** 额外字段（保留扩展，不参与逻辑） */
    private Map<String, Object> extra;
}
