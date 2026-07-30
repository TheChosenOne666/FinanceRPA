package com.finrpa.workflows.validator;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.finrpa.common.exception.BusinessException;
import com.finrpa.common.exception.ThrowUtils;
import com.finrpa.common.response.ErrorCode;
import com.finrpa.skills.entity.SkillMetaEO;
import com.finrpa.skills.mapper.SkillMetaMapper;
import com.finrpa.workflows.dto.request.WorkflowAddRequest;
import com.finrpa.workflows.enums.IndustryEnum;
import com.finrpa.workflows.enums.RiskLevelEnum;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 工作流模板校验器
 *
 * <p>校验内容：
 * <ul>
 *   <li>行业 / 风险等级枚举合法</li>
 *   <li>params / steps JSON 格式合法</li>
 *   <li>steps 中引用的 Skill 存在且启用</li>
 *   <li>params_mapping 中的 {{param}} 引用在 params 中定义</li>
 * </ul>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Slf4j
@Component
public class WorkflowValidator {

    /** 参数引用模板语法正则：{{param_name}} */
    private static final Pattern PARAM_REF_PATTERN = Pattern.compile("\\{\\{(\\w+)}}");

    @Resource
    private SkillMetaMapper skillMetaMapper;

    /**
     * 校验工作流模板创建请求。
     *
     * @param request 创建请求
     */
    public void validate(WorkflowAddRequest request) {
        // 1. 基础字段校验
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR, "请求不能为空");
        ThrowUtils.throwIf(StringUtils.isBlank(request.getName()), ErrorCode.PARAMS_ERROR, "模板名称不能为空");
        ThrowUtils.throwIf(StringUtils.isBlank(request.getIndustry()), ErrorCode.PARAMS_ERROR, "行业不能为空");
        ThrowUtils.throwIf(StringUtils.isBlank(request.getSteps()), ErrorCode.PARAMS_ERROR, "步骤不能为空");

        // 2. 枚举校验
        ThrowUtils.throwIf(IndustryEnum.getEnumByValue(request.getIndustry()) == null,
                ErrorCode.PARAMS_ERROR, "行业不合法: " + request.getIndustry());
        ThrowUtils.throwIf(request.getRiskLevel() != null
                        && RiskLevelEnum.getEnumByValue(request.getRiskLevel()) == null,
                ErrorCode.PARAMS_ERROR, "风险等级不合法: " + request.getRiskLevel());

        // 3. params 默认为空数组
        String paramsJson = request.getParams() == null ? "[]" : request.getParams();
        String stepsJson = request.getSteps();

        // 4. 校验 steps 中的 Skill 引用
        validateSteps(stepsJson, paramsJson);
    }

    /**
     * 校验步骤 JSON 中引用的 Skill 是否合法。
     *
     * @param stepsJson  步骤 JSON 字符串
     * @param paramsJson 参数定义 JSON 字符串
     */
    private void validateSteps(String stepsJson, String paramsJson) {
        // 1. 提取 steps 中所有 skill 名
        List<String> skillNames = extractSkillNames(stepsJson);
        ThrowUtils.throwIf(skillNames.isEmpty(), ErrorCode.PARAM_VALIDATE_FAILED, "步骤中未引用任何 Skill");

        // 2. 查询数据库确认 Skill 存在且启用
        LambdaQueryWrapper<SkillMetaEO> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(SkillMetaEO::getName, skillNames);
        List<SkillMetaEO> skills = skillMetaMapper.selectList(wrapper);

        // 3. 校验每个 skill 引用
        Set<String> foundSkills = new HashSet<>();
        for (SkillMetaEO skill : skills) {
            foundSkills.add(skill.getName());
            ThrowUtils.throwIf(skill.getEnabled() == 0, ErrorCode.SKILL_REF_INVALID,
                    "Skill 已禁用: " + skill.getName());
        }

        // 4. 找出未找到的 Skill
        List<String> missingSkills = skillNames.stream()
                .filter(name -> !foundSkills.contains(name))
                .distinct()
                .toList();
        ThrowUtils.throwIf(!missingSkills.isEmpty(), ErrorCode.SKILL_REF_INVALID,
                "步骤引用了不存在的 Skill: " + missingSkills);

        // 5. 校验 params_mapping 中的 {{param}} 引用在 params 中定义
        Set<String> definedParams = extractParamNames(paramsJson);
        Set<String> referencedParams = extractReferencedParams(stepsJson);
        List<String> undefinedParams = referencedParams.stream()
                .filter(p -> !definedParams.contains(p))
                .toList();
        ThrowUtils.throwIf(!undefinedParams.isEmpty(), ErrorCode.PARAM_VALIDATE_FAILED,
                "参数映射引用了未定义的参数: " + undefinedParams);

        log.info("WorkflowValidator: 校验通过，引用 {} 个 Skill，{} 个参数", skillNames.size(), definedParams.size());
    }

    /**
     * 从 steps JSON 中提取所有 skill 名。
     * 简单解析：匹配 "skill":"xxx" 模式。
     */
    private List<String> extractSkillNames(String stepsJson) {
        List<String> names = new ArrayList<>();
        Pattern pattern = Pattern.compile("\"skill\"\\s*:\\s*\"(\\w+)\"");
        Matcher matcher = pattern.matcher(stepsJson);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }

    /**
     * 从 params JSON 中提取所有参数名。
     * 匹配 "name":"xxx" 模式。
     */
    private Set<String> extractParamNames(String paramsJson) {
        Set<String> names = new HashSet<>();
        Pattern pattern = Pattern.compile("\"name\"\\s*:\\s*\"(\\w+)\"");
        Matcher matcher = pattern.matcher(paramsJson);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }

    /**
     * 从 steps JSON 中提取所有 {{param}} 引用。
     */
    private Set<String> extractReferencedParams(String stepsJson) {
        Set<String> params = new HashSet<>();
        Matcher matcher = PARAM_REF_PATTERN.matcher(stepsJson);
        while (matcher.find()) {
            params.add(matcher.group(1));
        }
        return params;
    }
}
