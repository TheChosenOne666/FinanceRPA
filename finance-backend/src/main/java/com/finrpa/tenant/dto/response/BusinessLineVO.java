package com.finrpa.tenant.dto.response;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 业务线视图对象（GET /tenant/business-lines 列表项）
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
public class BusinessLineVO implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 业务线业务 ID */
    private String businessLineId;

    /** 业务线名称 */
    private String businessLineName;

    /** 业务线编码 */
    private String businessLineCode;

    /** 描述 */
    private String description;

    /** 排序序号 */
    private Integer sortOrder;

    /** 状态（0-禁用 1-启用） */
    private Integer status;
}
