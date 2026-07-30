package com.finrpa.skills.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finrpa.skills.dto.request.SkillAddRequest;
import com.finrpa.skills.dto.request.SkillUpdateRequest;
import com.finrpa.skills.dto.response.SkillVO;
import com.finrpa.skills.service.SkillRegistryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SkillController 单元测试
 *
 * <p>验证 Skill 元数据管理 API（列表、详情、注册、更新）的请求转发与响应格式。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
class SkillControllerTest {

    /** JSON 序列化工具 */
    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockMvc mockMvc;
    private SkillRegistryService skillRegistryService;

    @BeforeEach
    void setUp() {
        // 1. mock 依赖
        skillRegistryService = mock(SkillRegistryService.class);

        // 2. 构建 MockMvc
        SkillController controller = new SkillController();
        ReflectionTestUtils.setField(controller, "skillRegistryService", skillRegistryService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("Skill 列表 - 查询成功")
    void listSkills_Success() throws Exception {
        // 1. mock 分页结果
        SkillVO skillVO = new SkillVO();
        skillVO.setName("login");
        skillVO.setCategory("auth");
        Page<SkillVO> page = new Page<>(1, 10);
        page.setRecords(List.of(skillVO));
        page.setTotal(1);
        when(skillRegistryService.listSkills(any())).thenReturn(page);

        // 2. 执行请求并验证
        mockMvc.perform(get("/skills")
                        .param("current", "1")
                        .param("pageSize", "10")
                        .param("category", "auth"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.records[0].name").value("login"));
    }

    @Test
    @DisplayName("Skill 详情 - 查询成功")
    void getSkill_Success() throws Exception {
        // 1. mock 详情
        SkillVO skillVO = new SkillVO();
        skillVO.setName("login");
        skillVO.setCategory("auth");
        skillVO.setDescription("通用登录流程");
        when(skillRegistryService.getSkillByName(eq("login"))).thenReturn(skillVO);

        // 2. 执行请求并验证
        mockMvc.perform(get("/skills/login"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.name").value("login"))
                .andExpect(jsonPath("$.data.category").value("auth"));
    }

    @Test
    @DisplayName("注册 Skill - 成功")
    void registerSkill_Success() throws Exception {
        // 1. mock 注册结果
        SkillVO skillVO = new SkillVO();
        skillVO.setName("custom_skill");
        skillVO.setCategory("auth");
        when(skillRegistryService.registerSkill(any(SkillAddRequest.class))).thenReturn(skillVO);

        // 2. 构造请求体
        SkillAddRequest request = new SkillAddRequest();
        request.setName("custom_skill");
        request.setCategory("auth");
        request.setDescription("自定义 Skill");

        // 3. 执行请求并验证
        mockMvc.perform(post("/skills")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.name").value("custom_skill"));
    }

    @Test
    @DisplayName("更新 Skill - 成功")
    void updateSkill_Success() throws Exception {
        // 1. mock 更新结果
        when(skillRegistryService.updateSkill(eq("login"), any(SkillUpdateRequest.class))).thenReturn(true);

        // 2. 构造请求体
        SkillUpdateRequest request = new SkillUpdateRequest();
        request.setDescription("更新描述");
        request.setEnabled(0);

        // 3. 执行请求并验证
        mockMvc.perform(put("/skills/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(true));
    }
}
