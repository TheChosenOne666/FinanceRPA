package com.finrpa.skills.datagen;

import com.finrpa.skills.service.SkillRegistryService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Skill 元数据初始化器
 *
 * <p>应用启动时自动注册 7 个内置 Skill 元数据（login / session_keep_alive / form_fill /
 * search_and_select / pagination / table_extract / file_download）。</p>
 *
 * <p>特性：
 * <ul>
 *   <li>所有环境运行（非 dev 限定），保证生产环境 Skill 元数据齐全</li>
 *   <li>upsert 语义：已存在则更新元数据字段（不动 enabled），不存在则插入</li>
 *   <li>注册失败不阻止应用启动（仅记 error 日志），下次启动会重试</li>
 *   <li>不依赖 Python 服务在线（内置元数据硬编码在 SkillConstant）</li>
 * </ul>
 * </p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Slf4j
@Component
@Order(20)
public class SkillMetaInitializer implements ApplicationRunner {

    /** Skill 注册服务 */
    @Resource
    private SkillRegistryService skillRegistryService;

    /**
     * 应用启动时注册内置 Skill 元数据
     *
     * @param args 启动参数
     */
    @Override
    public void run(ApplicationArguments args) {
        try {
            log.info("开始注册内置 Skill 元数据...");
            skillRegistryService.registerBuiltinSkills();
            log.info("内置 Skill 元数据注册完成");
        } catch (Exception e) {
            // 注册失败不阻止应用启动，下次启动会重新 upsert
            log.error("内置 Skill 元数据注册失败，将在下次启动重试", e);
        }
    }
}
