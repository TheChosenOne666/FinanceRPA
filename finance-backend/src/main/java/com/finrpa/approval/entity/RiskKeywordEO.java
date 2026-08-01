package com.finrpa.approval.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.sql.Timestamp;

/**
 * 风险关键词实体（对应 rpa_risk_keyword 表）
 *
 * <p>全局共享的风险关键词库，覆盖银行 / 保险 / 证券 三大金融行业。
 * 由 {@code RiskKeywordInitializer} 启动时 upsert 内置关键词，管理员可通过 API 增删改。</p>
 *
 * <p>该表不参与租户隔离（无 org_id 字段），已加入 TenantLineHandler 忽略清单。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
@TableName("rpa_risk_keyword")
public class RiskKeywordEO implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID（数据库自增） */
    @TableField("id")
    private Long id;

    /** 关键词业务 ID（雪花算法） */
    @TableId(value = "keyword_id", type = IdType.ASSIGN_ID)
    private Long keywordId;

    /** 关键词文本（如"转账"、"银行卡号"） */
    @TableField("keyword")
    private String keyword;

    /** 所属行业：banking / insurance / securities */
    @TableField("industry")
    private String industry;

    /** 分类：high_risk_operation / sensitive_data / large_amount */
    @TableField("category")
    private String category;

    /** 风险类型：high / medium / low（用于判定最终风险等级） */
    @TableField("risk_type")
    private String riskType;

    /** 描述说明（可空） */
    @TableField("description")
    private String description;

    /** 启用状态（0-禁用 1-启用） */
    @TableField("enabled")
    private Integer enabled;

    /** 是否内置关键词（0-自定义 1-内置，内置关键词不可删除） */
    @TableField("builtin")
    private Integer builtin;

    /** 逻辑删除标识（0-未删除 1-已删除） */
    @TableLogic
    @TableField("deleted")
    private Integer deleted;

    /** 创建时间（由数据库 DEFAULT CURRENT_TIMESTAMP 自动填充） */
    @TableField("create_time")
    private Timestamp createTime;

    /** 更新时间（由数据库 DEFAULT CURRENT_TIMESTAMP 自动填充） */
    @TableField("update_time")
    private Timestamp updateTime;
}
