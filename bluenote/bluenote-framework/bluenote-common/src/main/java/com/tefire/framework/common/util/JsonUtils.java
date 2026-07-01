package com.tefire.framework.common.util;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import lombok.SneakyThrows;

/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-06-17 21:29:37
 * @Description: 解决Jackson不支持新日期API
 */
public class JsonUtils {
    
    private static ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    // 在类加载时进行初始化
    static {
        // 反序列化时忽略未知属性
        OBJECT_MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false); 
        // 序列化时忽略空的javaBean属性
        OBJECT_MAPPER.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        // JavaTimeModule 用于指定序列化和反序列化规则
        JavaTimeModule javaTimeModule = new JavaTimeModule();
        // 支持 LocalDateTime
        // 解决 LocalDateTime 的序列化问题
        OBJECT_MAPPER.registerModules(javaTimeModule); 
    }


    /**
     * 初始化：统一使用 Spring Boot 个性化配置的 ObjectMapper
     *
     * @param objectMapper
     */
    public static void init(ObjectMapper objectMapper) {
        OBJECT_MAPPER = objectMapper;
    }

    /**
     *  将对象转换为 JSON 字符串
     * @param obj
     * @return
     */   
    @SneakyThrows // 用于简化异常处理。它会将被标注的方法中的受检异常转换为不受检异常
    public static String toJsonString(Object object) {
        return OBJECT_MAPPER.writeValueAsString(object);
    }

    /**
     * 将 JSON 字符串转换为对象
     *
     * @param jsonStr
     * @param clazz
     * @return
     * @param <T>
     */
    @SneakyThrows
    public static <T> T parseObject(String jsonStr, Class<T> clazz) {
        if (StringUtils.isBlank(jsonStr)) {
            return null;
        }

        return OBJECT_MAPPER.readValue(jsonStr, clazz);
    }
}
