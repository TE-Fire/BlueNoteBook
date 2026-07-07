package com.tefire;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-07-07 08:44:39
 * @Description: 启动类
 */
@SpringBootApplication
@MapperScan("com.tefire.relation.domain.mapper")
@EnableFeignClients("com.tefire.user.api")
public class BlueNoteUserRelationBizApplication {
    public static void main(String[] args) {
        SpringApplication.run(BlueNoteUserRelationBizApplication.class, args);
    }
}