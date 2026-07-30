package com.finrpa.ai.client.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

/**
 * Python AI 服务 Skill 元数据响应 DTO
 *
 * <p>对应 Python {@code GET /api/v1/ai/skills} 端点返回的 SkillMetaItem，
 * 字段命名与 Python Pydantic camelCase 输出对齐。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
public class SkillInfoResponse implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** Skill 唯一标识 */
    private String name;

    /** 用途描述 */
    private String description;

    /** 分类 */
    private String category;

    /** 失败策略 */
    private String errorStrategy;

    /** 最大重试次数 */
    private Integer maxRetries;

    /** 参数 JSON Schema（Python Pydantic model_json_schema() 输出） */
    private Map<String, Object> paramsSchema;
}
