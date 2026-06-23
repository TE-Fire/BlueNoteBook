package com.tefire.framework.biz.context.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;

import com.tefire.framework.biz.context.filter.HeaderUserId2ContextFilter;

/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-06-23 22:28:46
 * @Description: 在spring加载时装配bean
 */
@AutoConfiguration
public class ContextAutoConfiguration {

    @Bean
    public FilterRegistrationBean<HeaderUserId2ContextFilter> filterFilterRegistrationBean() {
        HeaderUserId2ContextFilter filter = new HeaderUserId2ContextFilter();
        FilterRegistrationBean<HeaderUserId2ContextFilter> bean = new FilterRegistrationBean<>(filter);
        return bean;
    }
}
