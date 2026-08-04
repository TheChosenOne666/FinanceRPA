package com.finrpa.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.sql.Timestamp;

/**
 * 密码历史实体（对应 sys_password_history 表）
 *
 * <p>记录用户最近 N 次密码的 BCrypt 哈希值，用于新密码修改时校验是否重复使用。
 * 不参与租户隔离（无 org_id 字段）。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
@TableName("sys_password_history")
public class PasswordHistoryEO implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID（数据库自增） */
    @TableField("id")
    private Long id;

    /** 历史记录业务 ID（雪花算法） */
    @TableId(value = "history_id", type = IdType.ASSIGN_ID)
    private Long historyId;

    /** 用户业务 ID */
    @TableField("user_id")
    private Long userId;

    /** 密码 BCrypt 哈希值 */
    @TableField("password_hash")
    private String passwordHash;

    /** 创建时间 */
    @TableField("create_time")
    private Timestamp createTime;
}
