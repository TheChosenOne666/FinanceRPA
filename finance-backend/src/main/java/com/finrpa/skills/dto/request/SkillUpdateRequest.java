package com.finrpa.skills.dto.request;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * Skill 更新请求 DTO
 *
 * <p>仅允许更新描述、分类、参数 Schema、失败策略、重试次数、版本、启用状态；
 * name 不可修改（保证 Skill 身份稳定）。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
public class SkillUpdateRequest implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 用途描述 */
    private String description;

    /** 分类：auth / interaction / extraction */
    private String category;

    /** 参数 JSON Schema */
    private String paramSchema;

    /** 失败策略：RETRY / SKIP / ABORT */
    private String errorStrategy;

    /** 最大重试次数 */
    private Integer maxRetries;

    /** 版本号 */
    private String version;

    /** 启用状态（0-禁用 1-启用） */
    private Integer enabled;
}
