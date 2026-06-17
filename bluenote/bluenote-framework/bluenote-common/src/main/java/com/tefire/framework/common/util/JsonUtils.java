package com.tefire.framework.common.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.tefire.framework.common.constant.DateConstants;

import lombok.SneakyThrows;

/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-06-17 21:29:37
 * @Description: 解决Jackson不支持新日期API
 */
public class JsonUtils {
    
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    // 在类加载时进行初始化
    static {
        // 反序列化时忽略未知属性
        OBJECT_MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false); 
        // 序列化时忽略空的javaBean属性
        OBJECT_MAPPER.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        // JavaTimeModule 用于指定序列化和反序列化规则
        JavaTimeModule javaTimeModule = new JavaTimeModule();
        // 支持 LocalDateTime
        javaTimeModule.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(DateTimeFormatter.ofPattern(DateConstants.Y_M_D_H_M_S_FORMAT)));
        javaTimeModule.addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer(DateTimeFormatter.ofPattern(DateConstants.Y_M_D_H_M_S_FORMAT)));
        // 解决 LocalDateTime 的序列化问题
        OBJECT_MAPPER.registerModules(javaTimeModule); 
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
}
