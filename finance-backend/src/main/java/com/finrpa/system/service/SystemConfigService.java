package com.finrpa.system.service;

import com.finrpa.system.dto.request.SystemConfigUpdateRequest;
import com.finrpa.system.dto.response.SystemConfigVO;

import java.util.List;

/**
 * 系统配置服务接口
 *
 * <p>P3 统一配置中心：提供配置读取（带 30s 本地缓存）/ 列表 / 更新 / 刷新接口。
 * 业务模块通过 {@code getString / getInteger / getBoolean} 读取配置值，缺失时回退默认值。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
public interface SystemConfigService {

    /**
     * 读取字符串配置
     *
     * @param key          配置键
     * @param defaultValue 默认值（配置缺失或禁用时返回）
     * @return 配置值
     */
    String getString(String key, String defaultValue);

    /**
     * 读取整数配置
     *
     * @param key          配置键
     * @param defaultValue 默认值
     * @return 配置值
     */
    Integer getInteger(String key, Integer defaultValue);

    /**
     * 读取布尔配置
     *
     * @param key          配置键
     * @param defaultValue 默认值
     * @return 配置值
     */
    Boolean getBoolean(String key, Boolean defaultValue);

    /**
     * 查询全部配置项（设置页展示用）
     *
     * @return 配置列表
     */
    List<SystemConfigVO> listAll();

    /**
     * 按 config_key 更新配置
     *
     * @param key     配置键
     * @param request 更新请求
     * @return 更新后的配置 VO
     */
    SystemConfigVO updateConfig(String key, SystemConfigUpdateRequest request);

    /**
     * 刷新本地缓存（配置更新后由 Controller 自动调用；WebClient/MinioClient 重建由调用方处理）
     */
    void refreshCache();
}
