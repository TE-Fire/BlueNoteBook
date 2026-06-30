package com.tefire;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-06-25 20:30:41
 * @Description: 用户服务启动类
 */
@SpringBootApplication
@MapperScan("com.tefire.user.biz.domain.mapper")
@EnableFeignClients(basePackages = {"com.tefire.oss.api", "com.tefire.generator.api"})
public class BlueNoteUserBizApplication {
     public static void main(String[] args) {
        SpringApplication.run(BlueNoteUserBizApplication.class, args);
    }
}
