package com.finrpa.skills.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.finrpa.ai.client.AiServiceClient;
import com.finrpa.ai.client.dto.SkillInfoResponse;
import com.finrpa.common.exception.BusinessException;
import com.finrpa.common.response.ErrorCode;
import com.finrpa.skills.dto.request.SkillAddRequest;
import com.finrpa.skills.dto.request.SkillQueryRequest;
import com.finrpa.skills.dto.request.SkillUpdateRequest;
import com.finrpa.skills.dto.response.SkillVO;
import com.finrpa.skills.entity.SkillMetaEO;
import com.finrpa.skills.mapper.SkillMetaMapper;
import com.finrpa.skills.service.impl.SkillRegistryServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * SkillRegistryServiceImpl 单元测试
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@ExtendWith(MockitoExtension.class)
class SkillRegistryServiceImplTest {

    @Mock
    private SkillMetaMapper skillMetaMapper;

    @Mock
    private AiServiceClient aiServiceClient;

    @InjectMocks
    private SkillRegistryServiceImpl skillRegistryService;

    // region listSkills

    @Test
    @DisplayName("Skill 列表 - 分页查询成功")
    void listSkills_Success() {
        // 1. mock 查询结果
        SkillMetaEO skill = createSkill("login", "auth", 1);
        Page<SkillMetaEO> page = new Page<>(1, 10);
        page.setRecords(List.of(skill));
        page.setTotal(1);
        when(skillMetaMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        // 2. 执行查询
        SkillQueryRequest request = new SkillQueryRequest();
        request.setCurrent(1);
        request.setPageSize(10);
        IPage<SkillVO> result = skillRegistryService.listSkills(request);

        // 3. 验证
        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0).getName()).isEqualTo("login");
    }

    @Test
    @DisplayName("Skill 列表 - pageSize 超限抛异常")
    void listSkills_PageSizeExceeded() {
        SkillQueryRequest request = new SkillQueryRequest();
        request.setPageSize(101);
        assertThatThrownBy(() -> skillRegistryService.listSkills(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("每页数量不能超过 100");
    }

    // endregion

    // region getSkillByName

    @Test
    @DisplayName("Skill 详情 - 查询成功")
    void getSkillByName_Success() {
        SkillMetaEO skill = createSkill("login", "auth", 1);
        when(skillMetaMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(skill);

        SkillVO result = skillRegistryService.getSkillByName("login");

        assertThat(result.getName()).isEqualTo("login");
        assertThat(result.getCategory()).isEqualTo("auth");
    }

    @Test
    @DisplayName("Skill 详情 - 不存在抛异常")
    void getSkillByName_NotFound() {
        when(skillMetaMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> skillRegistryService.getSkillByName("not_exist"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Skill 不存在");
    }

    @Test
    @DisplayName("Skill 详情 - name 为空抛参数异常")
    void getSkillByName_BlankName() {
        assertThatThrownBy(() -> skillRegistryService.getSkillByName(""))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Skill name 不能为空");
    }

    // endregion

    // region registerSkill

    @Test
    @DisplayName("注册 Skill - 成功")
    void registerSkill_Success() {
        // 1. name 不重复
        when(skillMetaMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        // 2. Python 校验通过
        SkillInfoResponse pythonSkill = new SkillInfoResponse();
        pythonSkill.setName("custom_skill");
        when(aiServiceClient.getSkills()).thenReturn(List.of(pythonSkill));
        // 3. 插入成功
        when(skillMetaMapper.insert(any(SkillMetaEO.class))).thenReturn(1);

        SkillAddRequest request = new SkillAddRequest();
        request.setName("custom_skill");
        request.setDescription("自定义 Skill");
        request.setCategory("auth");
        SkillVO result = skillRegistryService.registerSkill(request);

        assertThat(result.getName()).isEqualTo("custom_skill");
    }

    @Test
    @DisplayName("注册 Skill - name 重复抛异常")
    void registerSkill_DuplicateName() {
        when(skillMetaMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(createSkill("login", "auth", 1));

        SkillAddRequest request = new SkillAddRequest();
        request.setName("login");
        request.setCategory("auth");

        assertThatThrownBy(() -> skillRegistryService.registerSkill(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Skill 已存在");
    }

    @Test
    @DisplayName("注册 Skill - Python 不存在该 name 抛异常")
    void registerSkill_NotInPython() {
        when(skillMetaMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(aiServiceClient.getSkills()).thenReturn(List.of());

        SkillAddRequest request = new SkillAddRequest();
        request.setName("ghost_skill");
        request.setCategory("auth");

        assertThatThrownBy(() -> skillRegistryService.registerSkill(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Python 侧不存在 Skill");
    }

    @Test
    @DisplayName("注册 Skill - Python 服务不可用抛异常")
    void registerSkill_PythonUnavailable() {
        when(skillMetaMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(aiServiceClient.getSkills()).thenThrow(new RuntimeException("connection refused"));

        SkillAddRequest request = new SkillAddRequest();
        request.setName("custom_skill");
        request.setCategory("auth");

        assertThatThrownBy(() -> skillRegistryService.registerSkill(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Python 服务不可用");
    }

    @Test
    @DisplayName("注册 Skill - 非法分类抛异常")
    void registerSkill_InvalidCategory() {
        SkillAddRequest request = new SkillAddRequest();
        request.setName("custom_skill");
        request.setCategory("invalid_category");

        assertThatThrownBy(() -> skillRegistryService.registerSkill(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无效的分类");
    }

    // endregion

    // region updateSkill

    @Test
    @DisplayName("更新 Skill - 成功")
    void updateSkill_Success() {
        SkillMetaEO existing = createSkill("login", "auth", 1);
        existing.setId(1L);
        when(skillMetaMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);
        when(skillMetaMapper.updateById(any(SkillMetaEO.class))).thenReturn(1);

        SkillUpdateRequest request = new SkillUpdateRequest();
        request.setDescription("更新后的描述");
        request.setEnabled(0);

        Boolean result = skillRegistryService.updateSkill("login", request);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("更新 Skill - 不存在抛异常")
    void updateSkill_NotFound() {
        when(skillMetaMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        SkillUpdateRequest request = new SkillUpdateRequest();
        request.setDescription("xxx");

        assertThatThrownBy(() -> skillRegistryService.updateSkill("not_exist", request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Skill 不存在");
    }

    // endregion

    // region isEnabled

    @Test
    @DisplayName("isEnabled - 存在且启用返回 true")
    void isEnabled_Enabled() {
        when(skillMetaMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(createSkill("login", "auth", 1));

        assertThat(skillRegistryService.isEnabled("login")).isTrue();
    }

    @Test
    @DisplayName("isEnabled - 已禁用返回 false")
    void isEnabled_Disabled() {
        when(skillMetaMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(createSkill("login", "auth", 0));

        assertThat(skillRegistryService.isEnabled("login")).isFalse();
    }

    @Test
    @DisplayName("isEnabled - 不存在返回 false")
    void isEnabled_NotFound() {
        when(skillMetaMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        assertThat(skillRegistryService.isEnabled("not_exist")).isFalse();
    }

    @Test
    @DisplayName("isEnabled - name 为空返回 false")
    void isEnabled_BlankName() {
        assertThat(skillRegistryService.isEnabled("")).isFalse();
        verifyNoInteractions(skillMetaMapper);
    }

    // endregion

    // region registerBuiltinSkills

    @Test
    @DisplayName("内置 Skill 注册 - 全新插入 7 个")
    void registerBuiltinSkills_AllNew() {
        // 1. 全部不存在
        when(skillMetaMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(skillMetaMapper.insert(any(SkillMetaEO.class))).thenReturn(1);

        // 2. 执行注册
        skillRegistryService.registerBuiltinSkills();

        // 3. 验证插入 7 次
        verify(skillMetaMapper, times(7)).insert(any(SkillMetaEO.class));
        verify(skillMetaMapper, never()).updateById(any(SkillMetaEO.class));
    }

    @Test
    @DisplayName("内置 Skill 注册 - 已存在则更新")
    void registerBuiltinSkills_ExistingUpdate() {
        // 1. login 已存在，其他不存在
        SkillMetaEO existing = createSkill("login", "auth", 0);
        existing.setId(1L);
        when(skillMetaMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(existing)  // 第一次查询 login 返回已存在
                .thenReturn(null);     // 后续 6 个返回 null
        when(skillMetaMapper.updateById(any(SkillMetaEO.class))).thenReturn(1);
        when(skillMetaMapper.insert(any(SkillMetaEO.class))).thenReturn(1);

        // 2. 执行注册
        skillRegistryService.registerBuiltinSkills();

        // 3. 验证：1 次 update + 6 次 insert
        verify(skillMetaMapper, times(1)).updateById(any(SkillMetaEO.class));
        verify(skillMetaMapper, times(6)).insert(any(SkillMetaEO.class));
    }

    // endregion

    // region 辅助方法

    /**
     * 创建测试用 Skill 实体
     *
     * @param name     Skill name
     * @param category 分类
     * @param enabled  启用状态
     * @return Skill 实体
     */
    private SkillMetaEO createSkill(String name, String category, int enabled) {
        SkillMetaEO skill = new SkillMetaEO();
        skill.setSkillId(1L);
        skill.setName(name);
        skill.setDescription("测试 Skill");
        skill.setCategory(category);
        skill.setErrorStrategy("RETRY");
        skill.setMaxRetries(2);
        skill.setVersion("1.0.0");
        skill.setEnabled(enabled);
        return skill;
    }

    // endregion
}
