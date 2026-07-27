package com.finrpa.common.util;

import jakarta.validation.constraints.NotNull;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * Spring 上下文获取工具
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Component
public class SpringContextUtils implements ApplicationContextAware {

    /**
     * Spring 应用上下文
     */
    private static ApplicationContext applicationContext;

    /**
     * 注入 Spring 应用上下文
     *
     * @param applicationContext Spring 应用上下文
     * @throws BeansException 上下文注入异常
     */
    @Override
    public void setApplicationContext(@NotNull ApplicationContext applicationContext) throws BeansException {
        SpringContextUtils.applicationContext = applicationContext;
    }

    /**
     * 根据 Bean 名称获取 Bean 实例
     *
     * @param beanName Bean 名称
     * @return Bean 实例
     */
    public static Object getBean(String beanName) {
        return applicationContext.getBean(beanName);
    }

    /**
     * 根据 Bean 类型获取 Bean 实例
     *
     * @param beanClass Bean 类型
     * @param <T>       Bean 泛型
     * @return Bean 实例
     */
    public static <T> T getBean(Class<T> beanClass) {
        return applicationContext.getBean(beanClass);
    }

    /**
     * 根据 Bean 名称与类型获取 Bean 实例
     *
     * @param beanName  Bean 名称
     * @param beanClass Bean 类型
     * @param <T>       Bean 泛型
     * @return Bean 实例
     */
    public static <T> T getBean(String beanName, Class<T> beanClass) {
        return applicationContext.getBean(beanName, beanClass);
    }
}
