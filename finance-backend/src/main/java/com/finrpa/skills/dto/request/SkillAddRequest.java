package com.finrpa.skills.dto.request;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * Skill 新增请求 DTO
 *
 * <p>用户注册自定义 Skill 时提交。注册时会同步调 Python 校验 name 存在性。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
public class SkillAddRequest implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** Skill 唯一标识（对应 Python skill_name） */
    private String name;

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
}
