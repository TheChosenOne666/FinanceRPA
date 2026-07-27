package com.finrpa.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.boot.jackson.JsonComponent;
import org.springframework.context.annotation.Bean;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

/**
 * Spring MVC JSON 配置
 *
 * <p>Long 转 String 防止前端 JS 精度丢失（金融场景金额、ID 均为 Long）。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@JsonComponent
public class JsonConfig {

    /**
     * 构建 ObjectMapper，注册 Long 转 String 序列化模块
     *
     * @param builder Spring 提供的 Jackson 构建器
     * @return 配置好 Long 转 String 模块的 ObjectMapper 实例
     */
    @Bean
    public ObjectMapper jacksonObjectMapper(Jackson2ObjectMapperBuilder builder) {
        // 1. 创建 ObjectMapper
        ObjectMapper objectMapper = builder.createXmlMapper(false).build();
        // 2. 注册 Long 转 String 模块
        SimpleModule module = new SimpleModule();
        module.addSerializer(Long.class, ToStringSerializer.instance);
        module.addSerializer(Long.TYPE, ToStringSerializer.instance);
        objectMapper.registerModule(module);
        return objectMapper;
    }
}
