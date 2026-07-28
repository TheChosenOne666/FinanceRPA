package com.finrpa.tenant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.sql.Timestamp;

/**
 * 业务线实体（对应 enterprise_business_line 表）
 *
 * <p>属于某个组织，参与 TenantLineHandler 自动过滤。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
@TableName("enterprise_business_line")
public class BusinessLineEO implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 业务线业务 ID（UUID） */
    @TableField("business_line_id")
    private String businessLineId;

    /** 所属组织 ID */
    @TableField("org_id")
    private String orgId;

    /** 业务线名称 */
    @TableField("business_line_name")
    private String businessLineName;

    /** 业务线编码 */
    @TableField("business_line_code")
    private String businessLineCode;

    /** 描述 */
    @TableField("description")
    private String description;

    /** 排序序号 */
    @TableField("sort_order")
    private Integer sortOrder;

    /** 状态（0-禁用 1-启用） */
    @TableField("status")
    private Integer status;

    /** 逻辑删除标识（0-未删除 1-已删除） */
    @TableField("deleted")
    private Integer deleted;

    /** 创建时间（由数据库 DEFAULT CURRENT_TIMESTAMP 自动填充） */
    @TableField("create_time")
    private Timestamp createTime;

    /** 更新时间（由数据库 DEFAULT CURRENT_TIMESTAMP 自动填充） */
    @TableField("update_time")
    private Timestamp updateTime;
}
