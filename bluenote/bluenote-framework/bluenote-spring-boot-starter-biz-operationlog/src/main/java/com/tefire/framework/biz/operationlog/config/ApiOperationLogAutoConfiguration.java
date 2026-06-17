package com.tefire.framework.biz.operationlog.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

import com.tefire.framework.biz.operationlog.aspect.ApiOperationLogAspect;

/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-06-17 22:09:17
 * @Description: 自动配置类，通过@Bean 将ApiOperationLogAspect实例注入到spring容器
 */
@AutoConfiguration
public class ApiOperationLogAutoConfiguration {
    
    @Bean
    public ApiOperationLogAspect apiOperationLogAspect() {
        return new ApiOperationLogAspect();
    }
}
