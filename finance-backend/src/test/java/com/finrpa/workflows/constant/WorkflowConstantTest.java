package com.finrpa.workflows.constant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finrpa.skills.constant.SkillConstant;
import com.finrpa.workflows.entity.WorkflowTemplateEO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WorkflowConstant 6 个金融场景模板常量结构校验
 *
 * <p>验证内置模板的 JSON 结构合法性、Skill 引用合法性、参数引用闭环，
 * 确保启动注册时 WorkflowValidator 不会因常量定义错误而失败。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
class WorkflowConstantTest {

    /** JSON 序列化工具 */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** {{param}} 占位符正则 */
    private static final Pattern PARAM_REF_PATTERN = Pattern.compile("\\{\\{(\\w+)}}");

    /** 合法的行业值 */
    private static final Set<String> VALID_INDUSTRIES = Set.of("banking", "insurance", "securities");

    /** 合法的风险等级值 */
    private static final Set<String> VALID_RISK_LEVELS = Set.of("low", "medium", "high", "critical");

    /** 7 个内置 Skill 名称集合 */
    private static final Set<String> VALID_SKILL_NAMES = Set.of(
            SkillConstant.SKILL_LOGIN,
            SkillConstant.SKILL_SESSION_KEEP_ALIVE,
            SkillConstant.SKILL_FORM_FILL,
            SkillConstant.SKILL_SEARCH_AND_SELECT,
            SkillConstant.SKILL_PAGINATION,
            SkillConstant.SKILL_TABLE_EXTRACT,
            SkillConstant.SKILL_FILE_DOWNLOAD
    );

    @Test
    @DisplayName("内置模板共 6 个")
    void builtinTemplates_HasSize6() {
        assertThat(WorkflowConstant.BUILTIN_TEMPLATES).hasSize(6);
    }

    @Test
    @DisplayName("每个模板的 name 唯一")
    void builtinTemplates_NameUnique() {
        Set<String> names = new HashSet<>();
        for (WorkflowTemplateEO template : WorkflowConstant.BUILTIN_TEMPLATES) {
            assertThat(names.add(template.getName()))
                    .as("模板名称重复: %s", template.getName())
                    .isTrue();
        }
    }

    @Test
    @DisplayName("每个模板的 industry / riskLevel 合法")
    void builtinTemplates_IndustryAndRiskLevelValid() {
        for (WorkflowTemplateEO template : WorkflowConstant.BUILTIN_TEMPLATES) {
            assertThat(template.getIndustry())
                    .as("模板 %s 的 industry 非法", template.getName())
                    .isIn(VALID_INDUSTRIES);
            assertThat(template.getRiskLevel())
                    .as("模板 %s 的 riskLevel 非法", template.getName())
                    .isIn(VALID_RISK_LEVELS);
        }
    }

    @Test
    @DisplayName("每个模板的 params 是合法 JSON 数组且每个参数含 name/type/required/encrypted")
    void builtinTemplates_ParamsJsonValid() throws Exception {
        for (WorkflowTemplateEO template : WorkflowConstant.BUILTIN_TEMPLATES) {
            JsonNode paramsNode = objectMapper.readTree(template.getParams());

            assertThat(paramsNode.isArray())
                    .as("模板 %s 的 params 不是 JSON 数组", template.getName())
                    .isTrue();
            assertThat(paramsNode.size())
                    .as("模板 %s 的 params 为空", template.getName())
                    .isGreaterThan(0);

            for (JsonNode param : paramsNode) {
                assertThat(param.has("name"))
                        .as("模板 %s 的参数缺少 name 字段", template.getName())
                        .isTrue();
                assertThat(param.has("type"))
                        .as("模板 %s 的参数 %s 缺少 type 字段", template.getName(), param.get("name").asText())
                        .isTrue();
                assertThat(param.has("required"))
                        .as("模板 %s 的参数 %s 缺少 required 字段", template.getName(), param.get("name").asText())
                        .isTrue();
                assertThat(param.has("encrypted"))
                        .as("模板 %s 的参数 %s 缺少 encrypted 字段", template.getName(), param.get("name").asText())
                        .isTrue();
            }
        }
    }

    @Test
    @DisplayName("每个模板的 steps 是合法 JSON 数组且每步含 skill 字段")
    void builtinTemplates_StepsJsonValid() throws Exception {
        for (WorkflowTemplateEO template : WorkflowConstant.BUILTIN_TEMPLATES) {
            JsonNode stepsNode = objectMapper.readTree(template.getSteps());

            assertThat(stepsNode.isArray())
                    .as("模板 %s 的 steps 不是 JSON 数组", template.getName())
                    .isTrue();
            assertThat(stepsNode.size())
                    .as("模板 %s 的 steps 为空", template.getName())
                    .isGreaterThan(0);

            for (JsonNode step : stepsNode) {
                assertThat(step.has("skill"))
                        .as("模板 %s 的步骤缺少 skill 字段", template.getName())
                        .isTrue();
                String skillName = step.get("skill").asText();
                assertThat(skillName)
                        .as("模板 %s 引用了非法 Skill: %s", template.getName(), skillName)
                        .isIn(VALID_SKILL_NAMES);
            }
        }
    }

    @Test
    @DisplayName("steps 中的 {{param}} 引用都在 params 中定义")
    void builtinTemplates_ParamReferencesClosed() throws Exception {
        for (WorkflowTemplateEO template : WorkflowConstant.BUILTIN_TEMPLATES) {
            // 1. 收集 params 中定义的参数名
            JsonNode paramsNode = objectMapper.readTree(template.getParams());
            Set<String> definedParams = new HashSet<>();
            for (JsonNode param : paramsNode) {
                definedParams.add(param.get("name").asText());
            }

            // 2. 收集 steps 中 {{param}} 引用的参数名
            Matcher matcher = PARAM_REF_PATTERN.matcher(template.getSteps());
            List<String> referencedParams = new ArrayList<>();
            while (matcher.find()) {
                referencedParams.add(matcher.group(1));
            }

            // 3. 验证所有引用都在定义中
            for (String ref : referencedParams) {
                assertThat(definedParams)
                        .as("模板 %s 的 steps 引用了未定义的参数: %s", template.getName(), ref)
                        .contains(ref);
            }
        }
    }

    @Test
    @DisplayName("login_password 参数标记为 encrypted=true")
    void builtinTemplates_LoginPasswordEncrypted() throws Exception {
        for (WorkflowTemplateEO template : WorkflowConstant.BUILTIN_TEMPLATES) {
            JsonNode paramsNode = objectMapper.readTree(template.getParams());
            for (JsonNode param : paramsNode) {
                if ("login_password".equals(param.get("name").asText())) {
                    assertThat(param.get("encrypted").asBoolean())
                            .as("模板 %s 的 login_password 未标记为加密", template.getName())
                            .isTrue();
                }
            }
        }
    }

    @Test
    @DisplayName("每个模板至少包含 login 步骤")
    void builtinTemplates_HasLoginStep() throws Exception {
        for (WorkflowTemplateEO template : WorkflowConstant.BUILTIN_TEMPLATES) {
            JsonNode stepsNode = objectMapper.readTree(template.getSteps());
            boolean hasLogin = false;
            for (JsonNode step : stepsNode) {
                if (SkillConstant.SKILL_LOGIN.equals(step.get("skill").asText())) {
                    hasLogin = true;
                    break;
                }
            }
            assertThat(hasLogin)
                    .as("模板 %s 缺少 login 步骤", template.getName())
                    .isTrue();
        }
    }

    @Test
    @DisplayName("行业分布：banking 3 个 / insurance 2 个 / securities 1 个")
    void builtinTemplates_IndustryDistribution() {
        long bankingCount = WorkflowConstant.BUILTIN_TEMPLATES.stream()
                .filter(t -> "banking".equals(t.getIndustry()))
                .count();
        long insuranceCount = WorkflowConstant.BUILTIN_TEMPLATES.stream()
                .filter(t -> "insurance".equals(t.getIndustry()))
                .count();
        long securitiesCount = WorkflowConstant.BUILTIN_TEMPLATES.stream()
                .filter(t -> "securities".equals(t.getIndustry()))
                .count();

        assertThat(bankingCount).isEqualTo(3);
        assertThat(insuranceCount).isEqualTo(2);
        assertThat(securitiesCount).isEqualTo(1);
    }
}
