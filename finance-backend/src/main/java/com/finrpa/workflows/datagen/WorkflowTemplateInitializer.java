package com.finrpa.workflows.datagen;

import com.finrpa.workflows.service.WorkflowService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 工作流模板初始化器
 *
 * <p>应用启动时自动注册 6 个内置金融场景工作流模板：
 * <ol>
 *   <li>银行流水下载（banking / medium）</li>
 *   <li>跨行转账核对（banking / high）</li>
 *   <li>对公贷款放款（banking / critical）</li>
 *   <li>保单申请填写（insurance / high）</li>
 *   <li>理赔审核提交（insurance / high）</li>
 *   <li>委托下单（securities / high）</li>
 * </ol>
 * </p>
 *
 * <p>特性：
 * <ul>
 *   <li>{@code @Order(30)}：晚于 SkillMetaInitializer（@Order(20)），确保 WorkflowValidator 校验 Skill 引用时 Skill 已注册</li>
 *   <li>upsert 语义：已存在则更新元数据字段（不动 enabled），不存在则插入</li>
 *   <li>注册失败不阻止应用启动（仅记 error 日志），下次启动会重试</li>
 * </ul>
 * </p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Slf4j
@Component
@Order(30)
public class WorkflowTemplateInitializer implements ApplicationRunner {

    /** 工作流模板服务 */
    @Resource
    private WorkflowService workflowService;

    /**
     * 应用启动时注册内置工作流模板
     *
     * @param args 启动参数
     */
    @Override
    public void run(ApplicationArguments args) {
        try {
            log.info("开始注册内置工作流模板...");
            workflowService.registerBuiltinWorkflows();
            log.info("内置工作流模板注册完成");
        } catch (Exception e) {
            // 注册失败不阻止应用启动，下次启动会重新 upsert
            log.error("内置工作流模板注册失败，将在下次启动重试", e);
        }
    }
}
