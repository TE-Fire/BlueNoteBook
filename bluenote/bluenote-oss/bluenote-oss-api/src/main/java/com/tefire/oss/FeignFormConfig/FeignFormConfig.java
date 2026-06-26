package com.tefire.oss.FeignFormConfig;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import feign.codec.Encoder;
import feign.form.spring.SpringFormEncoder;

/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-06-26 20:44:04
 * @Description: feign增强：支持表单提交，将对象编码为表单数据格式（如 application/x-www-form-urlencoded 或 multipart/form-data），以便在 HTTP 请求中使用
 */
@Configuration
public class FeignFormConfig {
    
    @Bean
    public Encoder feignFormEncoder() {
        return new SpringFormEncoder();
    }
}
