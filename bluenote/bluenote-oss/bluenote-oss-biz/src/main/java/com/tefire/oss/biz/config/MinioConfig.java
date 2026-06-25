package com.tefire.oss.biz.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.minio.MinioClient;
import jakarta.annotation.Resource;

/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-06-25 14:09:07
 * @Description: Minio 客户端配置类
 */
@Configuration
public class MinioConfig {
    
    @Resource
    private MinioProperties minioProperties;

    @Bean
    public MinioClient minioClient() {
        // 构建 Minio 客户端
        return MinioClient.builder()
            .endpoint(minioProperties.getEndpoint())
            .credentials(minioProperties.getAccessKey(), minioProperties.getSecretKey())
            .build();
    }
}
