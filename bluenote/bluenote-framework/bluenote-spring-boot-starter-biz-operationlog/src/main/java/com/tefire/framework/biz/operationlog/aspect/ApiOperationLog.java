package com.tefire.framework.biz.operationlog.aspect;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-06-17 21:43:25
 * @Description: 记录操作日志注解，用于方法
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
@Documented // 指示被标记的注解应该被javadoc工具记录
public @interface ApiOperationLog {

    /**
     * API 功能描述
     * @return
     */
    String description() default "";
}
