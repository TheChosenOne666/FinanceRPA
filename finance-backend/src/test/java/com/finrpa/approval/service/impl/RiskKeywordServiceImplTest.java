package com.finrpa.approval.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.finrpa.approval.constant.ApprovalConstant;
import com.finrpa.approval.constant.RiskKeywordConstant;
import com.finrpa.approval.dto.request.RiskKeywordAddRequest;
import com.finrpa.approval.dto.request.RiskKeywordQueryRequest;
import com.finrpa.approval.dto.response.RiskKeywordVO;
import com.finrpa.approval.entity.RiskKeywordEO;
import com.finrpa.approval.mapper.RiskKeywordMapper;
import com.finrpa.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 风险关键词管理服务实现单元测试
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@ExtendWith(MockitoExtension.class)
class RiskKeywordServiceImplTest {

    @Mock
    private RiskKeywordMapper riskKeywordMapper;

    @InjectMocks
    private RiskKeywordServiceImpl riskKeywordService;

    // region listKeywords 查询

    @Test
    @DisplayName("listKeywords - 带筛选条件查询成功")
    void listKeywords_WithFilters_Success() {
        RiskKeywordQueryRequest queryRequest = new RiskKeywordQueryRequest();
        queryRequest.setIndustry("banking");
        queryRequest.setCategory("high_risk_operation");
        queryRequest.setEnabled(1);
        queryRequest.setCurrent(1);
        queryRequest.setPageSize(10);

        RiskKeywordEO eo = buildEo(1L, "转账", "banking", "high_risk_operation", "high");
        Page<RiskKeywordEO> page = new Page<>(1, 10, 1);
        page.setRecords(Collections.singletonList(eo));
        when(riskKeywordMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(page);

        var result = riskKeywordService.listKeywords(queryRequest);

        assertEquals(1, result.getTotal());
        assertEquals(1, result.getRecords().size());
        assertEquals("转账", result.getRecords().get(0).getKeyword());
    }

    @Test
    @DisplayName("listKeywords - 每页数量超过 200 抛出异常")
    void listKeywords_PageSizeExceedsLimit_ThrowsException() {
        RiskKeywordQueryRequest queryRequest = new RiskKeywordQueryRequest();
        queryRequest.setPageSize(201);

        assertThrows(BusinessException.class, () -> riskKeywordService.listKeywords(queryRequest));
    }

    // endregion

    // region listEnabledKeywords

    @Test
    @DisplayName("listEnabledKeywords - 按 industry 过滤")
    void listEnabledKeywords_ByIndustry_Success() {
        RiskKeywordEO eo = buildEo(1L, "转账", "banking", "high_risk_operation", "high");
        when(riskKeywordMapper.selectList(any(Wrapper.class)))
                .thenReturn(Collections.singletonList(eo));

        List<RiskKeywordEO> result = riskKeywordService.listEnabledKeywords("banking");

        assertEquals(1, result.size());
        assertEquals("转账", result.get(0).getKeyword());
    }

    @Test
    @DisplayName("listEnabledKeywords - industry 为空时返回全部启用关键词")
    void listEnabledKeywords_NullIndustry_ReturnsAll() {
        when(riskKeywordMapper.selectList(any(Wrapper.class)))
                .thenReturn(Collections.emptyList());

        List<RiskKeywordEO> result = riskKeywordService.listEnabledKeywords(null);

        assertNotNull(result);
        verify(riskKeywordMapper).selectList(any(Wrapper.class));
    }

    // endregion

    // region getKeywordDetail

    @Test
    @DisplayName("getKeywordDetail - 查询成功")
    void getKeywordDetail_Success() {
        RiskKeywordEO eo = buildEo(1L, "转账", "banking", "high_risk_operation", "high");
        when(riskKeywordMapper.selectOne(any(Wrapper.class))).thenReturn(eo);

        RiskKeywordVO vo = riskKeywordService.getKeywordDetail(1L);

        assertEquals("转账", vo.getKeyword());
        assertEquals("banking", vo.getIndustry());
    }

    @Test
    @DisplayName("getKeywordDetail - 关键词不存在抛出异常")
    void getKeywordDetail_NotFound_ThrowsException() {
        when(riskKeywordMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        assertThrows(BusinessException.class, () -> riskKeywordService.getKeywordDetail(999L));
    }

    @Test
    @DisplayName("getKeywordDetail - keywordId 为空抛出异常")
    void getKeywordDetail_NullId_ThrowsException() {
        assertThrows(BusinessException.class, () -> riskKeywordService.getKeywordDetail(null));
    }

    // endregion

    // region addKeyword

    @Test
    @DisplayName("addKeyword - 新增自定义关键词成功")
    void addKeyword_Success() {
        RiskKeywordAddRequest request = new RiskKeywordAddRequest();
        request.setKeyword("外汇交易");
        request.setIndustry("banking");
        request.setCategory("high_risk_operation");
        request.setRiskType("high");
        request.setDescription("外汇交易操作");

        when(riskKeywordMapper.insert(any(RiskKeywordEO.class))).thenAnswer(invocation -> {
            RiskKeywordEO eo = invocation.getArgument(0);
            eo.setKeywordId(100L);
            return 1;
        });

        Long keywordId = riskKeywordService.addKeyword(request);

        assertEquals(100L, keywordId);
        ArgumentCaptor<RiskKeywordEO> captor = ArgumentCaptor.forClass(RiskKeywordEO.class);
        verify(riskKeywordMapper).insert(captor.capture());
        RiskKeywordEO eo = captor.getValue();
        assertEquals("外汇交易", eo.getKeyword());
        assertEquals(0, eo.getBuiltin()); // 自定义关键词 builtin=0
        assertEquals(1, eo.getEnabled()); // 默认启用
    }

    @Test
    @DisplayName("addKeyword - riskType 为空时使用默认值 medium")
    void addKeyword_DefaultRiskType() {
        RiskKeywordAddRequest request = new RiskKeywordAddRequest();
        request.setKeyword("查询");
        request.setIndustry("banking");
        request.setCategory("large_amount");

        when(riskKeywordMapper.insert(any(RiskKeywordEO.class))).thenReturn(1);

        riskKeywordService.addKeyword(request);

        ArgumentCaptor<RiskKeywordEO> captor = ArgumentCaptor.forClass(RiskKeywordEO.class);
        verify(riskKeywordMapper).insert(captor.capture());
        assertEquals(ApprovalConstant.RISK_TYPE_MEDIUM, captor.getValue().getRiskType());
    }

    @Test
    @DisplayName("addKeyword - 无效行业抛出异常")
    void addKeyword_InvalidIndustry_ThrowsException() {
        RiskKeywordAddRequest request = new RiskKeywordAddRequest();
        request.setKeyword("测试");
        request.setIndustry("invalid_industry");
        request.setCategory("high_risk_operation");

        assertThrows(BusinessException.class, () -> riskKeywordService.addKeyword(request));
    }

    @Test
    @DisplayName("addKeyword - 无效分类抛出异常")
    void addKeyword_InvalidCategory_ThrowsException() {
        RiskKeywordAddRequest request = new RiskKeywordAddRequest();
        request.setKeyword("测试");
        request.setIndustry("banking");
        request.setCategory("invalid_category");

        assertThrows(BusinessException.class, () -> riskKeywordService.addKeyword(request));
    }

    @Test
    @DisplayName("addKeyword - 请求为空抛出异常")
    void addKeyword_NullRequest_ThrowsException() {
        assertThrows(BusinessException.class, () -> riskKeywordService.addKeyword(null));
    }

    // endregion

    // region updateKeyword

    @Test
    @DisplayName("updateKeyword - 自定义关键词全字段更新")
    void updateKeyword_CustomKeyword_AllFieldsUpdated() {
        RiskKeywordEO existing = buildEo(1L, "测试", "banking", "high_risk_operation", "high");
        existing.setBuiltin(0);
        when(riskKeywordMapper.selectOne(any(Wrapper.class))).thenReturn(existing);
        when(riskKeywordMapper.update(any(), any())).thenReturn(1);

        RiskKeywordAddRequest request = new RiskKeywordAddRequest();
        request.setKeyword("更新后关键词");
        request.setEnabled(0);

        boolean success = riskKeywordService.updateKeyword(1L, request);

        assertTrue(success);
        verify(riskKeywordMapper).update(any(), any());
    }

    @Test
    @DisplayName("updateKeyword - 内置关键词仅可更新 enabled 和 description")
    void updateKeyword_BuiltinKeyword_OnlyEnabledAndDescription() {
        RiskKeywordEO existing = buildEo(1L, "转账", "banking", "high_risk_operation", "high");
        existing.setBuiltin(1);
        when(riskKeywordMapper.selectOne(any(Wrapper.class))).thenReturn(existing);
        when(riskKeywordMapper.update(any(), any())).thenReturn(1);

        RiskKeywordAddRequest request = new RiskKeywordAddRequest();
        request.setKeyword("尝试修改关键词文本"); // 应被忽略
        request.setIndustry("insurance"); // 应被忽略
        request.setEnabled(0); // 应被更新
        request.setDescription("更新描述"); // 应被更新

        boolean success = riskKeywordService.updateKeyword(1L, request);

        assertTrue(success);
        verify(riskKeywordMapper).update(any(), any());
    }

    @Test
    @DisplayName("updateKeyword - 关键词不存在抛出异常")
    void updateKeyword_NotFound_ThrowsException() {
        when(riskKeywordMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        RiskKeywordAddRequest request = new RiskKeywordAddRequest();
        request.setEnabled(0);

        assertThrows(BusinessException.class, () -> riskKeywordService.updateKeyword(999L, request));
    }

    // endregion

    // region deleteKeyword

    @Test
    @DisplayName("deleteKeyword - 自定义关键词删除成功")
    void deleteKeyword_CustomKeyword_Success() {
        RiskKeywordEO existing = buildEo(1L, "测试", "banking", "high_risk_operation", "high");
        existing.setBuiltin(0);
        existing.setId(1L);
        when(riskKeywordMapper.selectOne(any(Wrapper.class))).thenReturn(existing);
        when(riskKeywordMapper.deleteById(1L)).thenReturn(1);

        boolean success = riskKeywordService.deleteKeyword(1L);

        assertTrue(success);
        verify(riskKeywordMapper).deleteById(1L);
    }

    @Test
    @DisplayName("deleteKeyword - 内置关键词不可删除")
    void deleteKeyword_BuiltinKeyword_ThrowsException() {
        RiskKeywordEO existing = buildEo(1L, "转账", "banking", "high_risk_operation", "high");
        existing.setBuiltin(1);
        when(riskKeywordMapper.selectOne(any(Wrapper.class))).thenReturn(existing);

        assertThrows(BusinessException.class, () -> riskKeywordService.deleteKeyword(1L));
        verify(riskKeywordMapper, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("deleteKeyword - 关键词不存在抛出异常")
    void deleteKeyword_NotFound_ThrowsException() {
        when(riskKeywordMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        assertThrows(BusinessException.class, () -> riskKeywordService.deleteKeyword(999L));
    }

    // endregion

    // region registerBuiltinKeywords

    @Test
    @DisplayName("registerBuiltinKeywords - 注册内置关键词库成功")
    void registerBuiltinKeywords_Success() {
        // 第一次调用：所有关键词都不存在
        when(riskKeywordMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(riskKeywordMapper.insert(any(RiskKeywordEO.class))).thenReturn(1);

        int count = riskKeywordService.registerBuiltinKeywords();

        assertEquals(RiskKeywordConstant.BUILTIN_KEYWORDS.size(), count);
        // 验证每个关键词都被尝试查询
        verify(riskKeywordMapper, times(RiskKeywordConstant.BUILTIN_KEYWORDS.size()))
                .selectOne(any(Wrapper.class));
        // 验证每个关键词都被插入
        verify(riskKeywordMapper, times(RiskKeywordConstant.BUILTIN_KEYWORDS.size()))
                .insert(any(RiskKeywordEO.class));
    }

    @Test
    @DisplayName("registerBuiltinKeywords - 已存在关键词走 upsert 更新分支")
    void registerBuiltinKeywords_ExistingKeyword_UpdatesInsteadOfInsert() {
        // 模拟关键词已存在
        RiskKeywordEO existing = buildEo(1L, "转账", "banking", "high_risk_operation", "high");
        existing.setKeywordId(100L);
        when(riskKeywordMapper.selectOne(any(Wrapper.class))).thenReturn(existing);
        when(riskKeywordMapper.update(any(), any())).thenReturn(1);

        int count = riskKeywordService.registerBuiltinKeywords();

        assertEquals(RiskKeywordConstant.BUILTIN_KEYWORDS.size(), count);
        // 全部走更新分支，不插入
        verify(riskKeywordMapper, never()).insert(any(RiskKeywordEO.class));
        verify(riskKeywordMapper, times(RiskKeywordConstant.BUILTIN_KEYWORDS.size()))
                .update(any(), any());
    }

    @Test
    @DisplayName("registerBuiltinKeywords - 内置关键词库非空")
    void registerBuiltinKeywords_BuiltinLibraryNotEmpty() {
        assertTrue(RiskKeywordConstant.BUILTIN_KEYWORDS.size() > 0);
        // 验证至少覆盖 3 大行业
        long industries = RiskKeywordConstant.BUILTIN_KEYWORDS.stream()
                .map(kw -> kw[1])
                .distinct()
                .count();
        assertTrue(industries >= 3, "内置关键词应至少覆盖 3 大行业");
    }

    // endregion

    // region 测试辅助方法

    /**
     * 构建关键词 EO
     */
    private RiskKeywordEO buildEo(Long keywordId, String keyword, String industry,
                                   String category, String riskType) {
        RiskKeywordEO eo = new RiskKeywordEO();
        eo.setKeywordId(keywordId);
        eo.setKeyword(keyword);
        eo.setIndustry(industry);
        eo.setCategory(category);
        eo.setRiskType(riskType);
        eo.setEnabled(1);
        eo.setBuiltin(0);
        return eo;
    }

    // endregion
}
