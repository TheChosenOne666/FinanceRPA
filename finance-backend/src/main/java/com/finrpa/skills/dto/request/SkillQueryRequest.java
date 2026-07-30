package com.finrpa.skills.dto.request;

import com.finrpa.common.model.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * Skill 分页查询请求 DTO
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SkillQueryRequest extends PageRequest {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 分类筛选（auth / interaction / extraction） */
    private String category;

    /** 启用状态筛选（0-禁用 1-启用） */
    private Integer enabled;

    /** 名称关键词搜索 */
    private String searchText;
}
