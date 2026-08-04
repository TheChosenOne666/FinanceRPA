package com.finrpa.notification.service.impl;

import com.finrpa.common.exception.BusinessException;
import com.finrpa.notification.config.NotificationProperties;
import com.finrpa.notification.dto.request.ChannelConfigSaveRequest;
import com.finrpa.notification.entity.NotificationChannelConfigEO;
import com.finrpa.notification.mapper.NotificationChannelConfigMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 通知通道 Webhook 配置服务单元测试（P0-4）
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@ExtendWith(MockitoExtension.class)
class NotificationChannelConfigServiceImplTest {

    @Mock
    private NotificationProperties properties;

    @Mock
    private NotificationChannelConfigMapper mapper;

    @InjectMocks
    private NotificationChannelConfigServiceImpl service;

    /** 真实的 NotificationProperties 实例（用于测试内存写入） */
    private NotificationProperties realProperties;

    @BeforeEach
    void setUp() {
        // 1. 创建真实的 NotificationProperties 实例（含 wecom / dingtalk 子配置）
        realProperties = new NotificationProperties();
        ReflectionTestUtils.setField(service, "properties", realProperties);
    }

    // region saveConfig

    @Test
    @DisplayName("saveConfig - 数据库已有记录时更新并同步内存")
    void saveConfig_UpdatesExistingRecord() {
        // arrange
        String channel = "wecom";
        NotificationChannelConfigEO existing = new NotificationChannelConfigEO();
        existing.setChannel(channel);
        existing.setWebhookUrl("https://old.qyapi.weixin.qq.com/cgi-bin/webhook/send?key=old_token");
        existing.setSecret("");
        existing.setEnabled(1);
        when(mapper.selectById(channel)).thenReturn(existing);

        ChannelConfigSaveRequest request = new ChannelConfigSaveRequest();
        request.setWebhookUrl("https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=new_token");
        request.setSecret("");
        request.setEnabled(true);

        // act
        NotificationChannelConfigEO result = service.saveConfig(channel, request);

        // assert
        verify(mapper).updateById(existing);
        verify(mapper, never()).insert(any(NotificationChannelConfigEO.class));
        assertEquals("https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=new_token", result.getWebhookUrl());
        assertEquals(1, result.getEnabled());
        // 内存已同步
        assertEquals("https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=new_token",
                realProperties.getWecom().getWebhookUrl());
    }

    @Test
    @DisplayName("saveConfig - 数据库无记录时新增并同步内存")
    void saveConfig_InsertsNewRecord() {
        // arrange
        String channel = "dingtalk";
        when(mapper.selectById(channel)).thenReturn(null);

        ChannelConfigSaveRequest request = new ChannelConfigSaveRequest();
        request.setWebhookUrl("https://oapi.dingtalk.com/robot/send?access_token=abc123");
        request.setSecret("SEC123456");
        request.setEnabled(true);

        // act
        NotificationChannelConfigEO result = service.saveConfig(channel, request);

        // assert
        verify(mapper).insert(any(NotificationChannelConfigEO.class));
        verify(mapper, never()).updateById(any(NotificationChannelConfigEO.class));
        assertEquals(channel, result.getChannel());
        assertEquals("https://oapi.dingtalk.com/robot/send?access_token=abc123", result.getWebhookUrl());
        assertEquals("SEC123456", result.getSecret());
        assertEquals(1, result.getEnabled());
        // 内存已同步（含 secret）
        assertEquals("https://oapi.dingtalk.com/robot/send?access_token=abc123",
                realProperties.getDingtalk().getWebhookUrl());
        assertEquals("SEC123456", realProperties.getDingtalk().getSecret());
    }

    @Test
    @DisplayName("saveConfig - 无效通道类型抛 PARAMS_ERROR")
    void saveConfig_InvalidChannel_ThrowsException() {
        // arrange
        ChannelConfigSaveRequest request = new ChannelConfigSaveRequest();
        request.setWebhookUrl("https://example.com");
        request.setEnabled(true);

        // act + assert
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.saveConfig("invalid_channel", request));
        assertEquals(40000, ex.getCode());
    }

    @Test
    @DisplayName("saveConfig - enabled 为 null 抛 PARAMS_ERROR")
    void saveConfig_NullEnabled_ThrowsException() {
        // arrange
        ChannelConfigSaveRequest request = new ChannelConfigSaveRequest();
        request.setWebhookUrl("https://example.com");
        request.setEnabled(null);

        // act + assert
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.saveConfig("wecom", request));
        assertEquals(40000, ex.getCode());
    }

    // endregion

    // region isChannelEnabled

    @Test
    @DisplayName("isChannelEnabled - 配置存在且 enabled=1 且 URL 非空时返回 true")
    void isChannelEnabled_ReturnsTrue_WhenConfiguredAndEnabled() {
        // arrange
        NotificationChannelConfigEO config = new NotificationChannelConfigEO();
        config.setChannel("wecom");
        config.setWebhookUrl("https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=abc");
        config.setEnabled(1);
        when(mapper.selectById("wecom")).thenReturn(config);

        // act + assert
        assertTrue(service.isChannelEnabled("wecom"));
    }

    @Test
    @DisplayName("isChannelEnabled - enabled=0 时返回 false")
    void isChannelEnabled_ReturnsFalse_WhenDisabled() {
        // arrange
        NotificationChannelConfigEO config = new NotificationChannelConfigEO();
        config.setChannel("wecom");
        config.setWebhookUrl("https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=abc");
        config.setEnabled(0);
        when(mapper.selectById("wecom")).thenReturn(config);

        // act + assert
        assertFalse(service.isChannelEnabled("wecom"));
    }

    @Test
    @DisplayName("isChannelEnabled - webhookUrl 为空时返回 false")
    void isChannelEnabled_ReturnsFalse_WhenUrlEmpty() {
        // arrange
        NotificationChannelConfigEO config = new NotificationChannelConfigEO();
        config.setChannel("wecom");
        config.setWebhookUrl("");
        config.setEnabled(1);
        when(mapper.selectById("wecom")).thenReturn(config);

        // act + assert
        assertFalse(service.isChannelEnabled("wecom"));
    }

    @Test
    @DisplayName("isChannelEnabled - 数据库无记录时返回 false")
    void isChannelEnabled_ReturnsFalse_WhenNoRecord() {
        // arrange
        when(mapper.selectById("wecom")).thenReturn(null);

        // act + assert
        assertFalse(service.isChannelEnabled("wecom"));
    }

    // endregion

    // region getByChannel

    @Test
    @DisplayName("getByChannel - 返回数据库配置")
    void getByChannel_ReturnsConfig() {
        // arrange
        NotificationChannelConfigEO config = new NotificationChannelConfigEO();
        config.setChannel("dingtalk");
        config.setWebhookUrl("https://oapi.dingtalk.com/robot/send?access_token=abc");
        when(mapper.selectById("dingtalk")).thenReturn(config);

        // act
        NotificationChannelConfigEO result = service.getByChannel("dingtalk");

        // assert
        assertNotNull(result);
        assertEquals("dingtalk", result.getChannel());
    }

    @Test
    @DisplayName("getByChannel - 数据库无记录返回 null")
    void getByChannel_ReturnsNull_WhenNoRecord() {
        // arrange
        when(mapper.selectById("wecom")).thenReturn(null);

        // act
        NotificationChannelConfigEO result = service.getByChannel("wecom");

        // assert
        assertNull(result);
    }

    // endregion
}
