package com.finrpa.datagen;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 演示数据初始化器
 *
 * <p>应用启动入口，负责调度 {@link DemoDataGenerator} 生成演示数据。
 * 仅在 dev 环境运行，且确保幂等性（不会重复生成）。</p>
 *
 * <p>数据生成逻辑（含 {@code @Transactional}）已抽离到独立的 {@link DemoDataGenerator} Bean，
 * 避免同类内部调用导致 Spring AOP 事务代理失效。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
public class DemoDataInitializer implements ApplicationRunner {

    /** 演示数据生成器（独立 Bean，确保 @Transactional 生效） */
    private final DemoDataGenerator demoDataGenerator;

    /** 是否启用演示数据生成 */
    @Value("${demo.data.enabled:true}")
    private boolean demoDataEnabled;

    /**
     * 应用启动时执行演示数据初始化
     *
     * @param args 启动参数
     */
    @Override
    public void run(ApplicationArguments args) {
        if (!demoDataEnabled) {
            log.info("演示数据生成已禁用");
            return;
        }

        // 1. 检查是否已存在演示数据
        if (demoDataGenerator.isDemoDataExists()) {
            log.info("演示数据已存在，跳过生成");
            return;
        }

        // 2. 生成演示数据（通过 Spring 代理调用，事务生效）
        try {
            demoDataGenerator.generateDemoData();
            log.info("演示数据生成完成");
        } catch (Exception e) {
            log.error("演示数据生成失败", e);
            throw new RuntimeException("演示数据生成失败", e);
        }
    }
}
