package com.tefire.framework.biz.context.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

import com.tefire.framework.biz.context.interceptor.FeignRequestInterceptor;

/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-06-26 21:45:03
 * @Description: Feign 请求拦截器自动配置
 */
@AutoConfiguration
public class FeignContextAutoConfiguration {
    @Bean
    public FeignRequestInterceptor feignRequestInterceptor() {
        return new FeignRequestInterceptor();
    }
}
