package com.finrpa.approval.dto.response;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.sql.Timestamp;

/**
 * 风险关键词 VO
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
public class RiskKeywordVO implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 关键词业务 ID */
    private Long keywordId;

    /** 关键词文本 */
    private String keyword;

    /** 所属行业 */
    private String industry;

    /** 分类 */
    private String category;

    /** 风险类型 */
    private String riskType;

    /** 描述说明 */
    private String description;

    /** 启用状态 */
    private Integer enabled;

    /** 是否内置 */
    private Integer builtin;

    /** 创建时间 */
    private Timestamp createTime;

    /** 更新时间 */
    private Timestamp updateTime;
}
