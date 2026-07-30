package com.finrpa.skills.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.finrpa.common.response.BaseResponse;
import com.finrpa.common.response.ResultUtils;
import com.finrpa.skills.dto.request.SkillAddRequest;
import com.finrpa.skills.dto.request.SkillQueryRequest;
import com.finrpa.skills.dto.request.SkillUpdateRequest;
import com.finrpa.skills.dto.response.SkillVO;
import com.finrpa.skills.service.SkillRegistryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Skill 元数据管理控制器
 *
 * <p>对外端点（实际访问路径前缀 {@code /api/skills}）：
 * <ul>
 *   <li>GET /skills —— 分页查询 Skill 列表（支持分类/启用状态筛选与关键词搜索）</li>
 *   <li>GET /skills/{name} —— 查询指定 Skill 详情</li>
 *   <li>POST /skills —— 注册自定义 Skill（同步调 Python 校验存在性）</li>
 *   <li>PUT /skills/{name} —— 更新 Skill 元数据（不允许修改 name）</li>
 * </ul>
 * </p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Slf4j
@RestController
@RequestMapping("/skills")
@Tag(name = "Skill 元数据管理", description = "Skill 列表查询、详情、注册与更新")
public class SkillController {

    /** Skill 注册服务 */
    @Resource
    private SkillRegistryService skillRegistryService;

    // region 查询

    /**
     * 分页查询 Skill 列表
     *
     * @param queryRequest 查询请求（含分类、启用状态、关键词）
     * @return Skill 分页列表
     */
    @GetMapping
    @Operation(summary = "Skill 列表", description = "分页查询 Skill 元数据，支持分类与启用状态筛选")
    public BaseResponse<IPage<SkillVO>> listSkills(SkillQueryRequest queryRequest) {
        // 1. 查询 Skill 列表
        IPage<SkillVO> page = skillRegistryService.listSkills(queryRequest);
        return ResultUtils.success(page);
    }

    /**
     * 查询指定 Skill 详情
     *
     * @param name Skill 唯一标识
     * @return Skill 视图对象
     */
    @GetMapping("/{name}")
    @Operation(summary = "Skill 详情", description = "按 name 查询 Skill 元数据")
    public BaseResponse<SkillVO> getSkill(@PathVariable String name) {
        // 1. 查询 Skill 详情
        SkillVO skill = skillRegistryService.getSkillByName(name);
        return ResultUtils.success(skill);
    }

    // endregion

    // region 写操作

    /**
     * 注册自定义 Skill
     *
     * @param request 新增请求
     * @return 新建的 Skill 视图对象
     */
    @PostMapping
    @Operation(summary = "注册 Skill", description = "注册自定义 Skill，同步调 Python 校验 name 存在性")
    public BaseResponse<SkillVO> registerSkill(@RequestBody SkillAddRequest request) {
        // 1. 注册 Skill
        SkillVO skill = skillRegistryService.registerSkill(request);
        return ResultUtils.success(skill);
    }

    /**
     * 更新 Skill 元数据
     *
     * @param name    Skill 唯一标识
     * @param request 更新请求
     * @return 操作结果
     */
    @PutMapping("/{name}")
    @Operation(summary = "更新 Skill", description = "更新 Skill 元数据，不允许修改 name")
    public BaseResponse<Boolean> updateSkill(@PathVariable String name,
                                              @RequestBody SkillUpdateRequest request) {
        // 1. 更新 Skill
        Boolean result = skillRegistryService.updateSkill(name, request);
        return ResultUtils.success(result);
    }

    // endregion
}
