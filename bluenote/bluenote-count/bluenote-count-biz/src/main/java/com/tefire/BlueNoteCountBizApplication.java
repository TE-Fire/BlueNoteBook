package com.tefire;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.tefire.count.domain.mapper")
public class BlueNoteCountBizApplication {
    public static void main(String[] args) {
        SpringApplication.run(BlueNoteCountBizApplication.class, args);
    }
}