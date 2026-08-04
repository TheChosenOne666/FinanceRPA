package com.finrpa.approval.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.finrpa.approval.constant.ApprovalConstant;
import com.finrpa.approval.dto.request.ApprovalTimeoutConfigUpdateRequest;
import com.finrpa.approval.dto.response.ApprovalTimeoutConfigVO;
import com.finrpa.approval.entity.ApprovalTimeoutConfigEO;
import com.finrpa.approval.mapper.ApprovalTimeoutConfigMapper;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 审批超时阈值配置服务实现单元测试（P1 RSK-1）
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@ExtendWith(MockitoExtension.class)
class ApprovalTimeoutConfigServiceImplTest {

    @Mock
    private ApprovalTimeoutConfigMapper approvalTimeoutConfigMapper;

    @InjectMocks
    private ApprovalTimeoutConfigServiceImpl approvalTimeoutConfigService;

    // region listAll

    @Test
    @DisplayName("listAll - 查询全部超时配置成功")
    void listAll_Success() {
        // 1. 准备数据
        ApprovalTimeoutConfigEO high = buildEo(1L, "high", 30, 1);
        ApprovalTimeoutConfigEO critical = buildEo(2L, "critical", 60, 1);
        when(approvalTimeoutConfigMapper.selectList(any(QueryWrapper.class)))
                .thenReturn(Arrays.asList(high, critical));

        // 2. 调用
        List<ApprovalTimeoutConfigVO> result = approvalTimeoutConfigService.listAll();

        // 3. 验证
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getRiskLevel()).isEqualTo("high");
        assertThat(result.get(0).getTimeoutMinutes()).isEqualTo(30);
        assertThat(result.get(1).getRiskLevel()).isEqualTo("critical");
        assertThat(result.get(1).getTimeoutMinutes()).isEqualTo(60);
    }

    @Test
    @DisplayName("listAll - 无配置时返回空列表")
    void listAll_Empty() {
        when(approvalTimeoutConfigMapper.selectList(any(QueryWrapper.class)))
                .thenReturn(Collections.emptyList());

        List<ApprovalTimeoutConfigVO> result = approvalTimeoutConfigService.listAll();

        assertThat(result).isEmpty();
    }

    // endregion

    // region getTimeoutMinutesByRiskLevel

    @Test
    @DisplayName("getTimeoutMinutesByRiskLevel - 启用配置返回配置值")
    void getTimeoutMinutesByRiskLevel_EnabledConfig_ReturnsConfigValue() {
        ApprovalTimeoutConfigEO eo = buildEo(1L, "high", 45, 1);
        when(approvalTimeoutConfigMapper.selectOne(any(QueryWrapper.class))).thenReturn(eo);

        long result = approvalTimeoutConfigService.getTimeoutMinutesByRiskLevel("high");

        assertThat(result).isEqualTo(45);
    }

    @Test
    @DisplayName("getTimeoutMinutesByRiskLevel - 配置缺失时回退到 high 默认值 30 分钟")
    void getTimeoutMinutesByRiskLevel_MissingConfig_FallsBackToHighDefault() {
        when(approvalTimeoutConfigMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);

        long result = approvalTimeoutConfigService.getTimeoutMinutesByRiskLevel("high");

        assertThat(result).isEqualTo(ApprovalConstant.HIGH_APPROVAL_TIMEOUT_MINUTES);
    }

    @Test
    @DisplayName("getTimeoutMinutesByRiskLevel - 配置缺失时回退到 critical 默认值 60 分钟")
    void getTimeoutMinutesByRiskLevel_MissingConfig_FallsBackToCriticalDefault() {
        when(approvalTimeoutConfigMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);

        long result = approvalTimeoutConfigService.getTimeoutMinutesByRiskLevel("critical");

        assertThat(result).isEqualTo(ApprovalConstant.CRITICAL_APPROVAL_TIMEOUT_MINUTES);
    }

    @Test
    @DisplayName("getTimeoutMinutesByRiskLevel - 未知风险等级回退到默认 30 分钟")
    void getTimeoutMinutesByRiskLevel_UnknownRiskLevel_FallsBackToDefault() {
        when(approvalTimeoutConfigMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);

        long result = approvalTimeoutConfigService.getTimeoutMinutesByRiskLevel("unknown");

        assertThat(result).isEqualTo(ApprovalConstant.DEFAULT_APPROVAL_TIMEOUT_MINUTES);
    }

    @Test
    @DisplayName("getTimeoutMinutesByRiskLevel - 配置被禁用时回退到默认值")
    void getTimeoutMinutesByRiskLevel_DisabledConfig_FallsBackToDefault() {
        // 1. selectOne 查 enabled=1 的记录返回 null（被禁用）
        when(approvalTimeoutConfigMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);

        long result = approvalTimeoutConfigService.getTimeoutMinutesByRiskLevel("high");

        assertThat(result).isEqualTo(ApprovalConstant.HIGH_APPROVAL_TIMEOUT_MINUTES);
    }

    @Test
    @DisplayName("getTimeoutMinutesByRiskLevel - 配置存在但 timeoutMinutes 为 0 时回退到默认值")
    void getTimeoutMinutesByRiskLevel_ZeroTimeout_FallsBackToDefault() {
        ApprovalTimeoutConfigEO eo = buildEo(1L, "high", 0, 1);
        when(approvalTimeoutConfigMapper.selectOne(any(QueryWrapper.class))).thenReturn(eo);

        long result = approvalTimeoutConfigService.getTimeoutMinutesByRiskLevel("high");

        assertThat(result).isEqualTo(ApprovalConstant.HIGH_APPROVAL_TIMEOUT_MINUTES);
    }

    // endregion

    // region updateConfig

    @Test
    @DisplayName("updateConfig - 成功更新超时配置")
    void updateConfig_Success() {
        // 1. 准备数据
        ApprovalTimeoutConfigEO existing = buildEo(1L, "high", 30, 1);
        ApprovalTimeoutConfigEO updated = buildEo(1L, "high", 60, 1);
        when(approvalTimeoutConfigMapper.selectOne(any(QueryWrapper.class)))
                .thenReturn(existing)   // 第一次查询：原记录
                .thenReturn(updated);   // 第二次查询：返回更新后
        when(approvalTimeoutConfigMapper.update(any(), any(UpdateWrapper.class))).thenReturn(1);

        // 2. 构建请求
        ApprovalTimeoutConfigUpdateRequest request = new ApprovalTimeoutConfigUpdateRequest();
        request.setTimeoutMinutes(60);
        request.setDescription("更新后的描述");
        request.setEnabled(1);

        // 3. 调用
        ApprovalTimeoutConfigVO result = approvalTimeoutConfigService.updateConfig("high", request);

        // 4. 验证
        assertThat(result).isNotNull();
        assertThat(result.getRiskLevel()).isEqualTo("high");
        assertThat(result.getTimeoutMinutes()).isEqualTo(60);

        // 5. 验证更新调用参数
        ArgumentCaptor<UpdateWrapper<ApprovalTimeoutConfigEO>> wrapperCaptor =
                ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(approvalTimeoutConfigMapper).update(any(), wrapperCaptor.capture());
    }

    @Test
    @DisplayName("updateConfig - 风险等级为空抛出异常")
    void updateConfig_BlankRiskLevel_ThrowsException() {
        ApprovalTimeoutConfigUpdateRequest request = new ApprovalTimeoutConfigUpdateRequest();
        request.setTimeoutMinutes(30);

        assertThrows(BusinessException.class,
                () -> approvalTimeoutConfigService.updateConfig("", request));
    }

    @Test
    @DisplayName("updateConfig - 无效风险等级抛出异常")
    void updateConfig_InvalidRiskLevel_ThrowsException() {
        ApprovalTimeoutConfigUpdateRequest request = new ApprovalTimeoutConfigUpdateRequest();
        request.setTimeoutMinutes(30);

        assertThrows(BusinessException.class,
                () -> approvalTimeoutConfigService.updateConfig("medium", request));
    }

    @Test
    @DisplayName("updateConfig - 请求为空抛出异常")
    void updateConfig_NullRequest_ThrowsException() {
        assertThrows(BusinessException.class,
                () -> approvalTimeoutConfigService.updateConfig("high", null));
    }

    @Test
    @DisplayName("updateConfig - 超时分钟数为 null 抛出异常")
    void updateConfig_NullTimeoutMinutes_ThrowsException() {
        ApprovalTimeoutConfigUpdateRequest request = new ApprovalTimeoutConfigUpdateRequest();

        assertThrows(BusinessException.class,
                () -> approvalTimeoutConfigService.updateConfig("high", request));
    }

    @Test
    @DisplayName("updateConfig - 超时分钟数 <= 0 抛出异常")
    void updateConfig_ZeroOrNegativeTimeout_ThrowsException() {
        ApprovalTimeoutConfigUpdateRequest request = new ApprovalTimeoutConfigUpdateRequest();
        request.setTimeoutMinutes(0);

        assertThrows(BusinessException.class,
                () -> approvalTimeoutConfigService.updateConfig("high", request));
    }

    @Test
    @DisplayName("updateConfig - 超时分钟数超过 1440 抛出异常")
    void updateConfig_ExceedsMaxTimeout_ThrowsException() {
        ApprovalTimeoutConfigUpdateRequest request = new ApprovalTimeoutConfigUpdateRequest();
        request.setTimeoutMinutes(1441);

        assertThrows(BusinessException.class,
                () -> approvalTimeoutConfigService.updateConfig("high", request));
    }

    @Test
    @DisplayName("updateConfig - 配置不存在抛出异常")
    void updateConfig_NotFound_ThrowsException() {
        when(approvalTimeoutConfigMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);

        ApprovalTimeoutConfigUpdateRequest request = new ApprovalTimeoutConfigUpdateRequest();
        request.setTimeoutMinutes(30);

        assertThrows(BusinessException.class,
                () -> approvalTimeoutConfigService.updateConfig("high", request));
    }

    @Test
    @DisplayName("updateConfig - 更新失败（数据库返回 0 行）抛出异常")
    void updateConfig_UpdateFails_ThrowsException() {
        ApprovalTimeoutConfigEO existing = buildEo(1L, "high", 30, 1);
        when(approvalTimeoutConfigMapper.selectOne(any(QueryWrapper.class))).thenReturn(existing);
        when(approvalTimeoutConfigMapper.update(any(), any(UpdateWrapper.class))).thenReturn(0);

        ApprovalTimeoutConfigUpdateRequest request = new ApprovalTimeoutConfigUpdateRequest();
        request.setTimeoutMinutes(60);

        assertThrows(BusinessException.class,
                () -> approvalTimeoutConfigService.updateConfig("high", request));
    }

    // endregion

    // region 测试辅助方法

    /**
     * 构建超时配置 EO
     */
    private ApprovalTimeoutConfigEO buildEo(Long configId, String riskLevel,
                                             Integer timeoutMinutes, Integer enabled) {
        ApprovalTimeoutConfigEO eo = new ApprovalTimeoutConfigEO();
        eo.setConfigId(configId);
        eo.setRiskLevel(riskLevel);
        eo.setTimeoutMinutes(timeoutMinutes);
        eo.setEnabled(enabled);
        eo.setDeleted(0);
        return eo;
    }

    // endregion
}
