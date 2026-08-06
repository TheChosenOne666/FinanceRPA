package com.finrpa.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.finrpa.common.exception.ThrowUtils;
import com.finrpa.common.response.ErrorCode;
import com.finrpa.system.dto.request.SystemConfigUpdateRequest;
import com.finrpa.system.dto.response.SystemConfigVO;
import com.finrpa.system.entity.SystemConfigEO;
import com.finrpa.system.mapper.SystemConfigMapper;
import com.finrpa.system.service.SystemConfigService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 * 系统配置服务实现（P3 统一配置中心）
 *
 * <p>采用「ConcurrentMap 本地缓存 + 30 秒 TTL」策略，兼顾读取性能与运行时热生效：
 * <ul>
 *   <li>读取路径：先查缓存，过期（30s）或未命中时整表重载</li>
 *   <li>更新路径：写入 DB 后立即置缓存过期，下次读自动重载</li>
 *   <li>手动刷新：{@code POST /api/system-config/refresh} 触发整表重载</li>
 * </ul>
 * </p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Slf4j
@Service
public class SystemConfigServiceImpl implements SystemConfigService {

    /** 系统配置 Mapper */
    @Resource
    private SystemConfigMapper systemConfigMapper;

    /** 配置缓存：config_key -> SystemConfigEO */
    private final ConcurrentMap<String, SystemConfigEO> cache = new ConcurrentHashMap<>();

    /** 缓存过期时间戳（ms） */
    private volatile long cacheExpireAt = 0L;

    /** 缓存 TTL：30 秒 */
    private static final long CACHE_TTL_MS = 30_000L;

    // region 缓存管理

    /**
     * 确保缓存新鲜：过期则触发整表重载
     */
    private void ensureCacheFresh() {
        if (System.currentTimeMillis() > cacheExpireAt) {
            loadCache();
        }
    }

    /**
     * 整表加载缓存（双重检查避免并发重复加载）
     */
    private synchronized void loadCache() {
        if (System.currentTimeMillis() <= cacheExpireAt) {
            return;
        }
        // 1. 查询全部启用状态的配置
        QueryWrapper<SystemConfigEO> wrapper = new QueryWrapper<>();
        wrapper.eq("status", 1);
        List<SystemConfigEO> list = systemConfigMapper.selectList(wrapper);

        // 2. 清空并重建缓存
        cache.clear();
        for (SystemConfigEO eo : list) {
            cache.put(eo.getConfigKey(), eo);
        }
        cacheExpireAt = System.currentTimeMillis() + CACHE_TTL_MS;
        log.debug("系统配置缓存已加载: {} 条", cache.size());
    }

    // endregion

    // region 读取

    /**
     * 读取字符串配置
     */
    @Override
    public String getString(String key, String defaultValue) {
        ensureCacheFresh();
        SystemConfigEO eo = cache.get(key);
        return eo != null ? eo.getConfigValue() : defaultValue;
    }

    /**
     * 读取整数配置
     */
    @Override
    public Integer getInteger(String key, Integer defaultValue) {
        String val = getString(key, null);
        if (val == null || val.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(val.trim());
        } catch (NumberFormatException e) {
            log.warn("配置项 {} 值 {} 转 Integer 失败，回退默认值 {}", key, val, defaultValue);
            return defaultValue;
        }
    }

    /**
     * 读取布尔配置（值 "true"/"1" 视为 true）
     */
    @Override
    public Boolean getBoolean(String key, Boolean defaultValue) {
        String val = getString(key, null);
        if (val == null || val.isBlank()) {
            return defaultValue;
        }
        return "true".equalsIgnoreCase(val.trim()) || "1".equals(val.trim());
    }

    // endregion

    // region 列表与更新

    /**
     * 查询全部配置项（含禁用项，按 config_key 升序）
     */
    @Override
    public List<SystemConfigVO> listAll() {
        QueryWrapper<SystemConfigEO> wrapper = new QueryWrapper<>();
        wrapper.orderByAsc("config_key");
        List<SystemConfigEO> list = systemConfigMapper.selectList(wrapper);
        return list.stream().map(eo -> {
            SystemConfigVO vo = new SystemConfigVO();
            BeanUtils.copyProperties(eo, vo);
            return vo;
        }).collect(Collectors.toList());
    }

    /**
     * 按 config_key 更新配置
     */
    @Override
    public SystemConfigVO updateConfig(String key, SystemConfigUpdateRequest request) {
        // 1. 参数校验
        ThrowUtils.throwIf(key == null || key.isBlank(),
                ErrorCode.PARAMS_ERROR, "配置键不能为空");
        ThrowUtils.throwIf(request == null || request.getConfigValue() == null,
                ErrorCode.PARAMS_ERROR, "更新请求与配置值不能为空");

        // 2. 查询原记录
        QueryWrapper<SystemConfigEO> wrapper = new QueryWrapper<>();
        wrapper.eq("config_key", key);
        SystemConfigEO existing = systemConfigMapper.selectOne(wrapper);
        ThrowUtils.throwIf(existing == null, ErrorCode.NOT_FOUND_ERROR, "配置项不存在: " + key);

        // 3. 构建更新字段
        UpdateWrapper<SystemConfigEO> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("config_key", key)
                .set("config_value", request.getConfigValue());
        if (request.getDescription() != null) {
            updateWrapper.set("description", request.getDescription());
        }
        if (request.getStatus() != null) {
            ThrowUtils.throwIf(request.getStatus() != 0 && request.getStatus() != 1,
                    ErrorCode.PARAMS_ERROR, "状态值只能为 0 或 1");
            updateWrapper.set("status", request.getStatus());
        }

        // 4. 执行更新
        int rows = systemConfigMapper.update(null, updateWrapper);
        ThrowUtils.throwIf(rows <= 0, ErrorCode.OPERATION_ERROR, "配置更新失败");

        // 5. 置缓存过期，下次读自动重载
        cacheExpireAt = 0L;
        log.info("更新系统配置: key={}, value={}", key, request.getConfigValue());

        // 6. 重新查询返回
        SystemConfigEO updated = systemConfigMapper.selectOne(wrapper);
        SystemConfigVO vo = new SystemConfigVO();
        BeanUtils.copyProperties(updated, vo);
        return vo;
    }

    /**
     * 刷新缓存
     */
    @Override
    public void refreshCache() {
        cacheExpireAt = 0L;
        ensureCacheFresh();
        log.info("系统配置缓存已手动刷新: {} 条", cache.size());
    }

    // endregion
}
