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
 * 审批人映射配置实体（对应 rpa_approval_route_config 表）
 *
 * <p>存储「风险等级 × 业务线 → 审批人」的映射规则。创建审批单时按以下顺序查找审批人：
 * <ol>
 *   <li>精确匹配：{@code (org_id, risk_level, business_line_id)}</li>
 *   <li>默认路由：{@code (org_id, risk_level, business_line_id IS NULL)}</li>
 *   <li>仍找不到：approver_id 留空，由审批中心手动认领</li>
 * </ol>
 *
 * <p>该表有 org_id 字段，但已加入 TenantLineHandler 忽略清单（与 sys_user 同列处理，
 * 对外 API 在 Service 层手动按 orgId 过滤）。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
@TableName("rpa_approval_route_config")
public class ApprovalRouteConfigEO implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID（数据库自增） */
    @TableField("id")
    private Long id;

    /** 配置业务 ID（雪花算法） */
    @TableId(value = "config_id", type = IdType.ASSIGN_ID)
    private Long configId;

    /** 组织业务 ID（雪花算法 ID） */
    @TableField("org_id")
    private Long orgId;

    /** 风险等级：high / critical */
    @TableField("risk_level")
    private String riskLevel;

    /** 业务线业务 ID（NULL 表示该风险等级的默认路由） */
    @TableField("business_line_id")
    private Long businessLineId;

    /** 审批人用户业务 ID（关联 sys_user.user_id） */
    @TableField("approver_user_id")
    private Long approverUserId;

    /** 审批人所属部门业务 ID（可空，关联 enterprise_department.dept_id） */
    @TableField("department_id")
    private Long departmentId;

    /** 描述说明（可空） */
    @TableField("description")
    private String description;

    /** 启用状态（0-禁用 1-启用） */
    @TableField("enabled")
    private Integer enabled;

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
