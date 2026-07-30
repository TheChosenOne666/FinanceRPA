package com.finrpa.skills.dto.response;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.sql.Timestamp;

/**
 * Skill 视图对象（返回前端）
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
public class SkillVO implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** Skill 业务 ID（雪花算法） */
    private Long skillId;

    /** Skill 唯一标识 */
    private String name;

    /** 用途描述 */
    private String description;

    /** 分类 */
    private String category;

    /** 参数 JSON Schema */
    private String paramSchema;

    /** 失败策略 */
    private String errorStrategy;

    /** 最大重试次数 */
    private Integer maxRetries;

    /** 版本号 */
    private String version;

    /** 启用状态（0-禁用 1-启用） */
    private Integer enabled;

    /** 创建时间 */
    private Timestamp createTime;

    /** 更新时间 */
    private Timestamp updateTime;
}
