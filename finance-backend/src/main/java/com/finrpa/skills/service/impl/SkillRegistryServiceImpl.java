package com.finrpa.skills.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.finrpa.ai.client.AiServiceClient;
import com.finrpa.ai.client.dto.SkillInfoResponse;
import com.finrpa.common.exception.BusinessException;
import com.finrpa.common.exception.ThrowUtils;
import com.finrpa.common.response.ErrorCode;
import com.finrpa.skills.constant.SkillConstant;
import com.finrpa.skills.dto.request.SkillAddRequest;
import com.finrpa.skills.dto.request.SkillQueryRequest;
import com.finrpa.skills.dto.request.SkillUpdateRequest;
import com.finrpa.skills.dto.response.SkillVO;
import com.finrpa.skills.entity.SkillMetaEO;
import com.finrpa.skills.enums.SkillCategoryEnum;
import com.finrpa.skills.mapper.SkillMetaMapper;
import com.finrpa.skills.service.SkillRegistryService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Skill 元数据注册服务实现
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Slf4j
@Service
public class SkillRegistryServiceImpl implements SkillRegistryService {

    /** Skill 元数据 Mapper */
    @Resource
    private SkillMetaMapper skillMetaMapper;

    /** Python AI 服务客户端（用于校验自定义 Skill 存在性） */
    @Resource
    private AiServiceClient aiServiceClient;

    // region 对外接口

    /**
     * 分页查询 Skill 元数据列表
     *
     * @param queryRequest 查询请求
     * @return Skill 分页列表
     */
    @Override
    public IPage<SkillVO> listSkills(SkillQueryRequest queryRequest) {
        // 1. 构建查询条件
        LambdaQueryWrapper<SkillMetaEO> wrapper = new LambdaQueryWrapper<>();
        // 分类筛选
        if (queryRequest.getCategory() != null && !queryRequest.getCategory().isBlank()) {
            wrapper.eq(SkillMetaEO::getCategory, queryRequest.getCategory());
        }
        // 启用状态筛选
        if (queryRequest.getEnabled() != null) {
            wrapper.eq(SkillMetaEO::getEnabled, queryRequest.getEnabled());
        }
        // 名称关键词搜索
        if (queryRequest.getSearchText() != null && !queryRequest.getSearchText().isBlank()) {
            wrapper.like(SkillMetaEO::getName, queryRequest.getSearchText());
        }
        // 默认按创建时间倒序
        wrapper.orderByDesc(SkillMetaEO::getCreateTime);

        // 2. 分页查询（限制 pageSize 防爬虫）
        long size = queryRequest.getPageSize();
        ThrowUtils.throwIf(size > 100, ErrorCode.PARAMS_ERROR, "每页数量不能超过 100");
        Page<SkillMetaEO> page = new Page<>(queryRequest.getCurrent(), size);
        IPage<SkillMetaEO> skillPage = skillMetaMapper.selectPage(page, wrapper);

        // 3. 转换为 VO
        return skillPage.convert(this::convertToVO);
    }

    /**
     * 按 name 查询 Skill 详情
     *
     * @param name Skill 唯一标识
     * @return Skill 视图对象
     */
    @Override
    public SkillVO getSkillByName(String name) {
        // 1. 校验参数
        ThrowUtils.throwIf(name == null || name.isBlank(), ErrorCode.PARAMS_ERROR, "Skill name 不能为空");

        // 2. 查询
        SkillMetaEO skill = queryByName(name);
        ThrowUtils.throwIf(skill == null, ErrorCode.SKILL_NOT_FOUND, "Skill 不存在: " + name);

        // 3. 转换为 VO
        return convertToVO(skill);
    }

    /**
     * 注册自定义 Skill（同步调 Python 校验 name 存在性）
     *
     * @param request 新增请求
     * @return 新建的 Skill 视图对象
     */
    @Override
    public SkillVO registerSkill(SkillAddRequest request) {
        // 1. 参数校验
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR, "请求体不能为空");
        ThrowUtils.throwIf(request.getName() == null || request.getName().isBlank(),
                ErrorCode.PARAMS_ERROR, "Skill name 不能为空");
        validateCategory(request.getCategory());

        // 2. 校验 name 不重复
        SkillMetaEO existing = queryByName(request.getName());
        ThrowUtils.throwIf(existing != null, ErrorCode.SKILL_ALREADY_EXISTS,
                "Skill 已存在: " + request.getName());

        // 3. 调 Python 校验 Skill 真实存在
        validateSkillExistsInPython(request.getName());

        // 4. 构建实体并插入
        SkillMetaEO skill = new SkillMetaEO();
        BeanUtils.copyProperties(request, skill);
        skill.setEnabled(1);
        skill.setVersion(request.getVersion() == null ? "1.0.0" : request.getVersion());
        skill.setErrorStrategy(request.getErrorStrategy() == null ? "RETRY" : request.getErrorStrategy());
        skill.setMaxRetries(request.getMaxRetries() == null ? 2 : request.getMaxRetries());

        int rows = skillMetaMapper.insert(skill);
        ThrowUtils.throwIf(rows <= 0, ErrorCode.OPERATION_ERROR, "Skill 注册失败");

        log.info("Skill 注册成功: name={}", request.getName());
        return convertToVO(skill);
    }

    /**
     * 更新 Skill 元数据（不允许修改 name）
     *
     * @param name    Skill 唯一标识
     * @param request 更新请求
     * @return 是否更新成功
     */
    @Override
    public Boolean updateSkill(String name, SkillUpdateRequest request) {
        // 1. 校验参数
        ThrowUtils.throwIf(name == null || name.isBlank(), ErrorCode.PARAMS_ERROR, "Skill name 不能为空");
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR, "请求体不能为空");
        if (request.getCategory() != null) {
            validateCategory(request.getCategory());
        }

        // 2. 查询原记录
        SkillMetaEO skill = queryByName(name);
        ThrowUtils.throwIf(skill == null, ErrorCode.SKILL_NOT_FOUND, "Skill 不存在: " + name);

        // 3. 按非空字段更新
        SkillMetaEO update = new SkillMetaEO();
        update.setId(skill.getId());
        if (request.getDescription() != null) {
            update.setDescription(request.getDescription());
        }
        if (request.getCategory() != null) {
            update.setCategory(request.getCategory());
        }
        if (request.getParamSchema() != null) {
            update.setParamSchema(request.getParamSchema());
        }
        if (request.getErrorStrategy() != null) {
            update.setErrorStrategy(request.getErrorStrategy());
        }
        if (request.getMaxRetries() != null) {
            update.setMaxRetries(request.getMaxRetries());
        }
        if (request.getVersion() != null) {
            update.setVersion(request.getVersion());
        }
        if (request.getEnabled() != null) {
            update.setEnabled(request.getEnabled());
        }

        int rows = skillMetaMapper.updateById(update);
        ThrowUtils.throwIf(rows <= 0, ErrorCode.OPERATION_ERROR, "Skill 更新失败");

        log.info("Skill 更新成功: name={}", name);
        return true;
    }

    /**
     * 启动时注册 7 个内置 Skill 元数据（upsert，不动 enabled 状态）
     */
    @Override
    public void registerBuiltinSkills() {
        // 1. 构建 7 个内置 Skill 元数据
        List<SkillMetaEO> builtins = buildBuiltinSkills();

        // 2. 逐个 upsert
        int inserted = 0;
        int updated = 0;
        for (SkillMetaEO builtin : builtins) {
            SkillMetaEO existing = queryByName(builtin.getName());
            if (existing == null) {
                // 不存在：插入
                skillMetaMapper.insert(builtin);
                inserted++;
            } else {
                // 已存在：仅更新元数据字段，不动 enabled（避免启动时把用户禁用的 Skill 重新启用）
                SkillMetaEO update = new SkillMetaEO();
                update.setId(existing.getId());
                update.setDescription(builtin.getDescription());
                update.setCategory(builtin.getCategory());
                update.setParamSchema(builtin.getParamSchema());
                update.setErrorStrategy(builtin.getErrorStrategy());
                update.setMaxRetries(builtin.getMaxRetries());
                update.setVersion(builtin.getVersion());
                skillMetaMapper.updateById(update);
                updated++;
            }
        }
        log.info("内置 Skill 注册完成: 新增 {} 个，更新 {} 个", inserted, updated);
    }

    /**
     * 校验指定 name 的 Skill 是否启用
     *
     * @param name Skill 唯一标识
     * @return true-存在且启用；false-不存在或已禁用
     */
    @Override
    public boolean isEnabled(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        SkillMetaEO skill = queryByName(name);
        return skill != null && skill.getEnabled() != null && skill.getEnabled() == 1;
    }

    // endregion

    // region 私有工具方法

    /**
     * 按 name 查询未删除的 Skill 元数据
     *
     * @param name Skill 唯一标识
     * @return Skill 实体；不存在时返回 null
     */
    private SkillMetaEO queryByName(String name) {
        LambdaQueryWrapper<SkillMetaEO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SkillMetaEO::getName, name);
        return skillMetaMapper.selectOne(wrapper);
    }

    /**
     * 校验分类是否合法
     *
     * @param category 分类值
     */
    private void validateCategory(String category) {
        ThrowUtils.throwIf(category == null || category.isBlank(), ErrorCode.PARAMS_ERROR, "分类不能为空");
        ThrowUtils.throwIf(SkillCategoryEnum.getEnumByValue(category) == null,
                ErrorCode.PARAMS_ERROR, "无效的分类: " + category);
    }

    /**
     * 调 Python 校验 Skill name 真实存在
     *
     * @param name Skill 唯一标识
     */
    private void validateSkillExistsInPython(String name) {
        List<SkillInfoResponse> pythonSkills;
        try {
            pythonSkills = aiServiceClient.getSkills();
        } catch (Exception e) {
            log.error("调用 Python 校验 Skill 存在性失败: name={}", name, e);
            throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE,
                    "Python 服务不可用，无法校验 Skill 存在性");
        }
        ThrowUtils.throwIf(pythonSkills == null, ErrorCode.AI_SERVICE_ERROR, "Python 返回空响应");

        Set<String> pythonNames = pythonSkills.stream()
                .map(SkillInfoResponse::getName)
                .collect(Collectors.toSet());
        ThrowUtils.throwIf(!pythonNames.contains(name), ErrorCode.SKILL_NOT_FOUND,
                "Python 侧不存在 Skill: " + name + "，请确认 Python 已实现该 Skill");
    }

    /**
     * 构建 7 个内置 Skill 元数据
     *
     * @return 内置 Skill 实体列表
     */
    private List<SkillMetaEO> buildBuiltinSkills() {
        List<SkillMetaEO> list = new ArrayList<>();

        // 1. login 登录
        list.add(buildBuiltin(SkillConstant.SKILL_LOGIN, "通用登录流程，含验证码处理",
                SkillConstant.CATEGORY_AUTH, SkillConstant.SCHEMA_LOGIN, "ABORT", 3));

        // 2. session_keep_alive 会话保活
        list.add(buildBuiltin(SkillConstant.SKILL_SESSION_KEEP_ALIVE, "会话监控，超时自动重登",
                SkillConstant.CATEGORY_AUTH, SkillConstant.SCHEMA_SESSION_KEEP_ALIVE, "RETRY", 2));

        // 3. form_fill 表单填充
        list.add(buildBuiltin(SkillConstant.SKILL_FORM_FILL, "智能表单填充，支持下拉框与日期选择器",
                SkillConstant.CATEGORY_INTERACTION, SkillConstant.SCHEMA_FORM_FILL, "RETRY", 2));

        // 4. search_and_select 搜索选择
        list.add(buildBuiltin(SkillConstant.SKILL_SEARCH_AND_SELECT, "搜索并从结果列表中选择项",
                SkillConstant.CATEGORY_INTERACTION, SkillConstant.SCHEMA_SEARCH_AND_SELECT, "RETRY", 2));

        // 5. pagination 分页遍历
        list.add(buildBuiltin(SkillConstant.SKILL_PAGINATION, "多页遍历并收集数据",
                SkillConstant.CATEGORY_INTERACTION, SkillConstant.SCHEMA_PAGINATION, "SKIP", 1));

        // 6. table_extract 表格提取
        list.add(buildBuiltin(SkillConstant.SKILL_TABLE_EXTRACT, "从页面表格提取结构化数据",
                SkillConstant.CATEGORY_EXTRACTION, SkillConstant.SCHEMA_TABLE_EXTRACT, "RETRY", 2));

        // 7. file_download 文件下载
        list.add(buildBuiltin(SkillConstant.SKILL_FILE_DOWNLOAD, "触发下载并等待文件保存",
                SkillConstant.CATEGORY_EXTRACTION, SkillConstant.SCHEMA_FILE_DOWNLOAD, "RETRY", 2));

        return list;
    }

    /**
     * 构建单个内置 Skill 元数据
     *
     * @param name           Skill name
     * @param description    用途描述
     * @param category       分类
     * @param paramSchema    参数 JSON Schema
     * @param errorStrategy  失败策略
     * @param maxRetries     最大重试次数
     * @return Skill 元数据实体
     */
    private SkillMetaEO buildBuiltin(String name, String description, String category,
                                     String paramSchema, String errorStrategy, int maxRetries) {
        SkillMetaEO skill = new SkillMetaEO();
        skill.setName(name);
        skill.setDescription(description);
        skill.setCategory(category);
        skill.setParamSchema(paramSchema);
        skill.setErrorStrategy(errorStrategy);
        skill.setMaxRetries(maxRetries);
        skill.setVersion("1.0.0");
        skill.setEnabled(1);
        return skill;
    }

    /**
     * 将 Skill 实体转换为 VO
     *
     * @param entity Skill 实体
     * @return Skill VO
     */
    private SkillVO convertToVO(SkillMetaEO entity) {
        SkillVO vo = new SkillVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }

    // endregion
}
