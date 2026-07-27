package com.finrpa;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * FinanceRPA Java 后端启动类
 *
 * <p>负责企业管理与编排：认证授权、多租户隔离、审批流编排、审计检索、
 * Dashboard 统计、LLM 管理、Agent 状态持久化、Skill 元数据、工作流模板、通知等。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@SpringBootApplication
@MapperScan("com.finrpa.**.mapper")
@EnableScheduling
@EnableAspectJAutoProxy(proxyTargetClass = true, exposeProxy = true)
public class FinRpaApplication {

    /**
     * 应用入口方法
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(FinRpaApplication.class, args);
    }
}
