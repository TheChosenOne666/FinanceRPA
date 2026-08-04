package com.finrpa.approval.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.finrpa.approval.dto.request.ApprovalRouteConfigAddRequest;
import com.finrpa.approval.dto.request.ApprovalRouteConfigQueryRequest;
import com.finrpa.approval.dto.request.ApprovalRouteConfigUpdateRequest;
import com.finrpa.approval.dto.response.ApprovalRouteConfigVO;
import com.finrpa.approval.entity.ApprovalRouteConfigEO;
import com.finrpa.approval.mapper.ApprovalRouteConfigMapper;
import com.finrpa.auth.entity.UserEO;
import com.finrpa.auth.mapper.UserMapper;
import com.finrpa.common.exception.BusinessException;
import com.finrpa.tenant.entity.BusinessLineEO;
import com.finrpa.tenant.mapper.BusinessLineMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * 审批人映射配置服务实现单元测试（P1 RSK-3）
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@ExtendWith(MockitoExtension.class)
class ApprovalRouteConfigServiceImplTest {

    @Mock
    private ApprovalRouteConfigMapper approvalRouteConfigMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private BusinessLineMapper businessLineMapper;

    @InjectMocks
    private ApprovalRouteConfigServiceImpl approvalRouteConfigService;

    private static final Long ORG_ID = 100L;
    private static final Long APPROVER_USER_ID = 200L;
    private static final Long BUSINESS_LINE_ID = 300L;

    // region listConfigs

    @Test
    @DisplayName("listConfigs - 查询成功并填充审批人姓名和业务线名称")
    void listConfigs_Success() {
        // 1. 准备分页数据
        ApprovalRouteConfigEO eo = buildEo(1L, ORG_ID, "high", BUSINESS_LINE_ID, APPROVER_USER_ID, 1);
        Page<ApprovalRouteConfigEO> page = new Page<>(1, 10, 1);
        page.setRecords(Collections.singletonList(eo));
        when(approvalRouteConfigMapper.selectPage(any(Page.class), any(QueryWrapper.class))).thenReturn(page);

        // 2. 模拟批量查询审批人姓名
        UserEO approver = new UserEO();
        approver.setUserId(APPROVER_USER_ID);
        approver.setRealName("张三");
        when(userMapper.selectByUserIds(any())).thenReturn(Collections.singletonList(approver));

        // 3. 模拟批量查询业务线名称
        BusinessLineEO bizLine = new BusinessLineEO();
        bizLine.setBusinessLineId(BUSINESS_LINE_ID);
        bizLine.setBusinessLineName("银行业务线");
        when(businessLineMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.singletonList(bizLine));

        // 4. 调用
        ApprovalRouteConfigQueryRequest queryRequest = new ApprovalRouteConfigQueryRequest();
        queryRequest.setCurrent(1);
        queryRequest.setPageSize(10);
        var result = approvalRouteConfigService.listConfigs(queryRequest, ORG_ID);

        // 5. 验证
        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getRecords()).hasSize(1);
        ApprovalRouteConfigVO vo = result.getRecords().get(0);
        assertThat(vo.getApproverUserId()).isEqualTo(APPROVER_USER_ID);
        assertThat(vo.getApproverName()).isEqualTo("张三");
        assertThat(vo.getBusinessLineName()).isEqualTo("银行业务线");
    }

    @Test
    @DisplayName("listConfigs - 默认路由配置展示「默认路由」名称")
    void listConfigs_DefaultRoute_ShowsDefaultName() {
        // 1. business_line_id 为 null 表示默认路由
        ApprovalRouteConfigEO eo = buildEo(1L, ORG_ID, "high", null, APPROVER_USER_ID, 1);
        Page<ApprovalRouteConfigEO> page = new Page<>(1, 10, 1);
        page.setRecords(Collections.singletonList(eo));
        when(approvalRouteConfigMapper.selectPage(any(Page.class), any(QueryWrapper.class))).thenReturn(page);

        UserEO approver = new UserEO();
        approver.setUserId(APPROVER_USER_ID);
        approver.setRealName("李四");
        when(userMapper.selectByUserIds(any())).thenReturn(Collections.singletonList(approver));

        ApprovalRouteConfigQueryRequest queryRequest = new ApprovalRouteConfigQueryRequest();
        queryRequest.setCurrent(1);
        queryRequest.setPageSize(10);
        var result = approvalRouteConfigService.listConfigs(queryRequest, ORG_ID);

        ApprovalRouteConfigVO vo = result.getRecords().get(0);
        assertThat(vo.getBusinessLineName()).isEqualTo("默认路由");
    }

    @Test
    @DisplayName("listConfigs - orgId 为空抛出异常")
    void listConfigs_NullOrgId_ThrowsException() {
        ApprovalRouteConfigQueryRequest queryRequest = new ApprovalRouteConfigQueryRequest();
        assertThrows(BusinessException.class,
                () -> approvalRouteConfigService.listConfigs(queryRequest, null));
    }

    @Test
    @DisplayName("listConfigs - 每页数量超过 200 抛出异常")
    void listConfigs_PageSizeExceedsLimit_ThrowsException() {
        ApprovalRouteConfigQueryRequest queryRequest = new ApprovalRouteConfigQueryRequest();
        queryRequest.setPageSize(201);
        assertThrows(BusinessException.class,
                () -> approvalRouteConfigService.listConfigs(queryRequest, ORG_ID));
    }

    // endregion

    // region getApproverUserId

    @Test
    @DisplayName("getApproverUserId - 精确匹配命中")
    void getApproverUserId_ExactMatch_Success() {
        ApprovalRouteConfigEO eo = buildEo(1L, ORG_ID, "high", BUSINESS_LINE_ID, APPROVER_USER_ID, 1);
        when(approvalRouteConfigMapper.selectExactMatch(ORG_ID, "high", BUSINESS_LINE_ID)).thenReturn(eo);

        Long result = approvalRouteConfigService.getApproverUserId(ORG_ID, "high", BUSINESS_LINE_ID);

        assertThat(result).isEqualTo(APPROVER_USER_ID);
        verify(approvalRouteConfigMapper, never()).selectDefaultRoute(anyLong(), anyString());
    }

    @Test
    @DisplayName("getApproverUserId - 精确匹配未命中时回退到默认路由")
    void getApproverUserId_FallbackToDefaultRoute_Success() {
        // 1. 精确匹配返回 null
        when(approvalRouteConfigMapper.selectExactMatch(ORG_ID, "high", BUSINESS_LINE_ID)).thenReturn(null);
        // 2. 默认路由命中
        ApprovalRouteConfigEO fallback = buildEo(2L, ORG_ID, "high", null, APPROVER_USER_ID, 1);
        when(approvalRouteConfigMapper.selectDefaultRoute(ORG_ID, "high")).thenReturn(fallback);

        Long result = approvalRouteConfigService.getApproverUserId(ORG_ID, "high", BUSINESS_LINE_ID);

        assertThat(result).isEqualTo(APPROVER_USER_ID);
    }

    @Test
    @DisplayName("getApproverUserId - 都找不到时返回 null")
    void getApproverUserId_NotFound_ReturnsNull() {
        when(approvalRouteConfigMapper.selectExactMatch(ORG_ID, "high", BUSINESS_LINE_ID)).thenReturn(null);
        when(approvalRouteConfigMapper.selectDefaultRoute(ORG_ID, "high")).thenReturn(null);

        Long result = approvalRouteConfigService.getApproverUserId(ORG_ID, "high", BUSINESS_LINE_ID);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("getApproverUserId - businessLineId 为 null 时仅查默认路由")
    void getApproverUserId_NullBusinessLineId_OnlyDefaultRoute() {
        ApprovalRouteConfigEO fallback = buildEo(1L, ORG_ID, "high", null, APPROVER_USER_ID, 1);
        when(approvalRouteConfigMapper.selectDefaultRoute(ORG_ID, "high")).thenReturn(fallback);

        Long result = approvalRouteConfigService.getApproverUserId(ORG_ID, "high", null);

        assertThat(result).isEqualTo(APPROVER_USER_ID);
        verify(approvalRouteConfigMapper, never()).selectExactMatch(anyLong(), anyString(), anyLong());
    }

    @Test
    @DisplayName("getApproverUserId - orgId 为 null 返回 null")
    void getApproverUserId_NullOrgId_ReturnsNull() {
        Long result = approvalRouteConfigService.getApproverUserId(null, "high", BUSINESS_LINE_ID);
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("getApproverUserId - riskLevel 为 null 返回 null")
    void getApproverUserId_NullRiskLevel_ReturnsNull() {
        Long result = approvalRouteConfigService.getApproverUserId(ORG_ID, null, BUSINESS_LINE_ID);
        assertThat(result).isNull();
    }

    // endregion

    // region addConfig

    @Test
    @DisplayName("addConfig - 新增带业务线的精确路由配置成功")
    void addConfig_WithBusinessLine_Success() {
        // 1. 审批人存在
        UserEO approver = new UserEO();
        approver.setUserId(APPROVER_USER_ID);
        when(userMapper.selectByUserId(APPROVER_USER_ID)).thenReturn(approver);

        // 2. 业务线存在
        BusinessLineEO bizLine = new BusinessLineEO();
        bizLine.setBusinessLineId(BUSINESS_LINE_ID);
        when(businessLineMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(bizLine);

        // 3. 无重复配置
        when(approvalRouteConfigMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);

        // 4. 插入成功
        when(approvalRouteConfigMapper.insert(any(ApprovalRouteConfigEO.class))).thenAnswer(invocation -> {
            ApprovalRouteConfigEO eo = invocation.getArgument(0);
            eo.setConfigId(1L);
            return 1;
        });

        ApprovalRouteConfigAddRequest request = new ApprovalRouteConfigAddRequest();
        request.setRiskLevel("high");
        request.setBusinessLineId(BUSINESS_LINE_ID);
        request.setApproverUserId(APPROVER_USER_ID);

        Long configId = approvalRouteConfigService.addConfig(ORG_ID, request);

        assertThat(configId).isEqualTo(1L);
    }

    @Test
    @DisplayName("addConfig - 新增默认路由配置成功（businessLineId 为 null）")
    void addConfig_DefaultRoute_Success() {
        UserEO approver = new UserEO();
        approver.setUserId(APPROVER_USER_ID);
        when(userMapper.selectByUserId(APPROVER_USER_ID)).thenReturn(approver);
        when(approvalRouteConfigMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);
        when(approvalRouteConfigMapper.insert(any(ApprovalRouteConfigEO.class))).thenAnswer(invocation -> {
            ApprovalRouteConfigEO eo = invocation.getArgument(0);
            eo.setConfigId(2L);
            return 1;
        });

        ApprovalRouteConfigAddRequest request = new ApprovalRouteConfigAddRequest();
        request.setRiskLevel("critical");
        request.setApproverUserId(APPROVER_USER_ID);

        Long configId = approvalRouteConfigService.addConfig(ORG_ID, request);

        assertThat(configId).isEqualTo(2L);
    }

    @Test
    @DisplayName("addConfig - orgId 为空抛出异常")
    void addConfig_NullOrgId_ThrowsException() {
        ApprovalRouteConfigAddRequest request = new ApprovalRouteConfigAddRequest();
        request.setRiskLevel("high");
        request.setApproverUserId(APPROVER_USER_ID);

        assertThrows(BusinessException.class,
                () -> approvalRouteConfigService.addConfig(null, request));
    }

    @Test
    @DisplayName("addConfig - 请求为空抛出异常")
    void addConfig_NullRequest_ThrowsException() {
        assertThrows(BusinessException.class,
                () -> approvalRouteConfigService.addConfig(ORG_ID, null));
    }

    @Test
    @DisplayName("addConfig - 风险等级为空抛出异常")
    void addConfig_BlankRiskLevel_ThrowsException() {
        ApprovalRouteConfigAddRequest request = new ApprovalRouteConfigAddRequest();
        request.setRiskLevel("");
        request.setApproverUserId(APPROVER_USER_ID);

        assertThrows(BusinessException.class,
                () -> approvalRouteConfigService.addConfig(ORG_ID, request));
    }

    @Test
    @DisplayName("addConfig - 无效风险等级抛出异常")
    void addConfig_InvalidRiskLevel_ThrowsException() {
        ApprovalRouteConfigAddRequest request = new ApprovalRouteConfigAddRequest();
        request.setRiskLevel("medium");
        request.setApproverUserId(APPROVER_USER_ID);

        assertThrows(BusinessException.class,
                () -> approvalRouteConfigService.addConfig(ORG_ID, request));
    }

    @Test
    @DisplayName("addConfig - 审批人 ID 为空抛出异常")
    void addConfig_NullApproverUserId_ThrowsException() {
        ApprovalRouteConfigAddRequest request = new ApprovalRouteConfigAddRequest();
        request.setRiskLevel("high");

        assertThrows(BusinessException.class,
                () -> approvalRouteConfigService.addConfig(ORG_ID, request));
    }

    @Test
    @DisplayName("addConfig - 审批人不存在抛出异常")
    void addConfig_ApproverNotFound_ThrowsException() {
        when(userMapper.selectByUserId(APPROVER_USER_ID)).thenReturn(null);

        ApprovalRouteConfigAddRequest request = new ApprovalRouteConfigAddRequest();
        request.setRiskLevel("high");
        request.setApproverUserId(APPROVER_USER_ID);

        assertThrows(BusinessException.class,
                () -> approvalRouteConfigService.addConfig(ORG_ID, request));
    }

    @Test
    @DisplayName("addConfig - 业务线不存在抛出异常")
    void addConfig_BusinessLineNotFound_ThrowsException() {
        UserEO approver = new UserEO();
        approver.setUserId(APPROVER_USER_ID);
        when(userMapper.selectByUserId(APPROVER_USER_ID)).thenReturn(approver);
        when(businessLineMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        ApprovalRouteConfigAddRequest request = new ApprovalRouteConfigAddRequest();
        request.setRiskLevel("high");
        request.setBusinessLineId(BUSINESS_LINE_ID);
        request.setApproverUserId(APPROVER_USER_ID);

        assertThrows(BusinessException.class,
                () -> approvalRouteConfigService.addConfig(ORG_ID, request));
    }

    @Test
    @DisplayName("addConfig - 重复配置抛出异常")
    void addConfig_DuplicateConfig_ThrowsException() {
        UserEO approver = new UserEO();
        approver.setUserId(APPROVER_USER_ID);
        when(userMapper.selectByUserId(APPROVER_USER_ID)).thenReturn(approver);

        // 1. 业务线存在（通过业务线检查，继续走到重复配置检查）
        BusinessLineEO bizLine = new BusinessLineEO();
        bizLine.setBusinessLineId(BUSINESS_LINE_ID);
        when(businessLineMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(bizLine);

        // 2. 重复配置已存在
        ApprovalRouteConfigEO existing = buildEo(1L, ORG_ID, "high", BUSINESS_LINE_ID, APPROVER_USER_ID, 1);
        when(approvalRouteConfigMapper.selectOne(any(QueryWrapper.class))).thenReturn(existing);

        ApprovalRouteConfigAddRequest request = new ApprovalRouteConfigAddRequest();
        request.setRiskLevel("high");
        request.setBusinessLineId(BUSINESS_LINE_ID);
        request.setApproverUserId(APPROVER_USER_ID);

        assertThrows(BusinessException.class,
                () -> approvalRouteConfigService.addConfig(ORG_ID, request));
    }

    // endregion

    // region updateConfig

    @Test
    @DisplayName("updateConfig - 更新审批人成功")
    void updateConfig_Success() {
        ApprovalRouteConfigEO existing = buildEo(1L, ORG_ID, "high", BUSINESS_LINE_ID, APPROVER_USER_ID, 1);
        when(approvalRouteConfigMapper.selectOne(any(QueryWrapper.class))).thenReturn(existing);

        UserEO newApprover = new UserEO();
        newApprover.setUserId(201L);
        when(userMapper.selectByUserId(201L)).thenReturn(newApprover);

        when(approvalRouteConfigMapper.update(any(), any())).thenReturn(1);

        ApprovalRouteConfigUpdateRequest request = new ApprovalRouteConfigUpdateRequest();
        request.setApproverUserId(201L);
        request.setEnabled(0);

        boolean success = approvalRouteConfigService.updateConfig(1L, request);

        assertThat(success).isTrue();
    }

    @Test
    @DisplayName("updateConfig - configId 为空抛出异常")
    void updateConfig_NullConfigId_ThrowsException() {
        ApprovalRouteConfigUpdateRequest request = new ApprovalRouteConfigUpdateRequest();
        assertThrows(BusinessException.class,
                () -> approvalRouteConfigService.updateConfig(null, request));
    }

    @Test
    @DisplayName("updateConfig - 配置不存在抛出异常")
    void updateConfig_NotFound_ThrowsException() {
        when(approvalRouteConfigMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);

        ApprovalRouteConfigUpdateRequest request = new ApprovalRouteConfigUpdateRequest();
        assertThrows(BusinessException.class,
                () -> approvalRouteConfigService.updateConfig(999L, request));
    }

    @Test
    @DisplayName("updateConfig - 审批人不存在抛出异常")
    void updateConfig_ApproverNotFound_ThrowsException() {
        ApprovalRouteConfigEO existing = buildEo(1L, ORG_ID, "high", BUSINESS_LINE_ID, APPROVER_USER_ID, 1);
        when(approvalRouteConfigMapper.selectOne(any(QueryWrapper.class))).thenReturn(existing);
        when(userMapper.selectByUserId(999L)).thenReturn(null);

        ApprovalRouteConfigUpdateRequest request = new ApprovalRouteConfigUpdateRequest();
        request.setApproverUserId(999L);

        assertThrows(BusinessException.class,
                () -> approvalRouteConfigService.updateConfig(1L, request));
    }

    // endregion

    // region deleteConfig

    @Test
    @DisplayName("deleteConfig - 逻辑删除成功")
    void deleteConfig_Success() {
        ApprovalRouteConfigEO existing = buildEo(1L, ORG_ID, "high", BUSINESS_LINE_ID, APPROVER_USER_ID, 1);
        existing.setId(10L);
        when(approvalRouteConfigMapper.selectOne(any(QueryWrapper.class))).thenReturn(existing);
        when(approvalRouteConfigMapper.deleteById(10L)).thenReturn(1);

        boolean success = approvalRouteConfigService.deleteConfig(1L);

        assertThat(success).isTrue();
        verify(approvalRouteConfigMapper).deleteById(10L);
    }

    @Test
    @DisplayName("deleteConfig - configId 为空抛出异常")
    void deleteConfig_NullConfigId_ThrowsException() {
        assertThrows(BusinessException.class,
                () -> approvalRouteConfigService.deleteConfig(null));
    }

    @Test
    @DisplayName("deleteConfig - 配置不存在抛出异常")
    void deleteConfig_NotFound_ThrowsException() {
        when(approvalRouteConfigMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);

        assertThrows(BusinessException.class,
                () -> approvalRouteConfigService.deleteConfig(999L));
    }

    // endregion

    // region 测试辅助方法

    /**
     * 构建审批人映射配置 EO
     */
    private ApprovalRouteConfigEO buildEo(Long configId, Long orgId, String riskLevel,
                                           Long businessLineId, Long approverUserId, Integer enabled) {
        ApprovalRouteConfigEO eo = new ApprovalRouteConfigEO();
        eo.setConfigId(configId);
        eo.setOrgId(orgId);
        eo.setRiskLevel(riskLevel);
        eo.setBusinessLineId(businessLineId);
        eo.setApproverUserId(approverUserId);
        eo.setEnabled(enabled);
        eo.setDeleted(0);
        return eo;
    }

    // endregion
}
