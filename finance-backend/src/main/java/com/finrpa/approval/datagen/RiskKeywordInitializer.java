package com.finrpa.approval.datagen;

import com.finrpa.approval.service.RiskKeywordService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 风险关键词初始化器
 *
 * <p>应用启动时自动注册内置关键词库（银行 / 保险 / 证券 三大行业）到 {@code rpa_risk_keyword} 表。</p>
 *
 * <p>特性：
 * <ul>
 *   <li>所有环境运行（非 dev 限定），保证生产环境关键词齐全</li>
 *   <li>upsert 语义：已存在则更新元数据字段（不动 enabled），不存在则插入</li>
 *   <li>注册失败不阻止应用启动（仅记 error 日志），下次启动会重试</li>
 *   <li>不依赖 Python 服务在线（关键词硬编码在 {@code RiskKeywordConstant}）</li>
 * </ul>
 * </p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Slf4j
@Component
@Order(25)
public class RiskKeywordInitializer implements ApplicationRunner {

    /** 风险关键词管理服务 */
    @Resource
    private RiskKeywordService riskKeywordService;

    /**
     * 应用启动时注册内置关键词库
     *
     * @param args 启动参数
     */
    @Override
    public void run(ApplicationArguments args) {
        try {
            log.info("开始注册内置风险关键词库...");
            int count = riskKeywordService.registerBuiltinKeywords();
            log.info("内置风险关键词库注册完成: 共 {} 个关键词", count);
        } catch (Exception e) {
            // 注册失败不阻止应用启动，下次启动会重新 upsert
            log.error("内置风险关键词库注册失败，将在下次启动重试", e);
        }
    }
}
