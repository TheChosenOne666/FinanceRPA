package com.finrpa.skills.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.finrpa.skills.dto.request.SkillAddRequest;
import com.finrpa.skills.dto.request.SkillQueryRequest;
import com.finrpa.skills.dto.request.SkillUpdateRequest;
import com.finrpa.skills.dto.response.SkillVO;

/**
 * Skill 元数据注册服务接口
 *
 * <p>提供 Skill 元数据的 CRUD、内置 Skill 自动注册、Python 存在性校验能力。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
public interface SkillRegistryService {

    /**
     * 分页查询 Skill 元数据列表
     *
     * @param queryRequest 查询请求（含分类筛选、启用状态筛选、关键词搜索）
     * @return Skill 分页列表
     */
    IPage<SkillVO> listSkills(SkillQueryRequest queryRequest);

    /**
     * 按 name 查询 Skill 详情
     *
     * @param name Skill 唯一标识
     * @return Skill 视图对象
     */
    SkillVO getSkillByName(String name);

    /**
     * 注册自定义 Skill（同步调 Python 校验 name 存在性）
     *
     * @param request 新增请求
     * @return 新建的 Skill 视图对象
     */
    SkillVO registerSkill(SkillAddRequest request);

    /**
     * 更新 Skill 元数据（不允许修改 name）
     *
     * @param name    Skill 唯一标识
     * @param request 更新请求
     * @return 是否更新成功
     */
    Boolean updateSkill(String name, SkillUpdateRequest request);

    /**
     * 启动时注册 7 个内置 Skill 元数据（upsert，不动 enabled 状态）
     */
    void registerBuiltinSkills();

    /**
     * 校验指定 name 的 Skill 是否启用（供 M3.4 工作流模板校验调用）
     *
     * @param name Skill 唯一标识
     * @return true-存在且启用；false-不存在或已禁用
     */
    boolean isEnabled(String name);
}
