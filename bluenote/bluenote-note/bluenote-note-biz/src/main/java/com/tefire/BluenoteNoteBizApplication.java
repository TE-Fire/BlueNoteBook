package com.tefire;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-06-30 12:37:03
 * @Description: 笔记服务启动类
 */
@SpringBootApplication
@MapperScan("com.tefire.note.biz.domain.mapper")
@EnableFeignClients(basePackages = {"com.tefire.kv.api", "com.tefire.generator.api", "com.tefire.user.api"})
public class BluenoteNoteBizApplication {
    public static void main(String[] args) {
        SpringApplication.run(BluenoteNoteBizApplication.class, args);
    }
}