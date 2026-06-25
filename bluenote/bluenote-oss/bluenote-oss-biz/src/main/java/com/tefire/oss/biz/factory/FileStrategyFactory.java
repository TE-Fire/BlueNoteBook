package com.tefire.oss.biz.factory;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.tefire.oss.biz.strategy.FileStrategy;
import com.tefire.oss.biz.strategy.impl.AliyunOSSFileStrategy;
import com.tefire.oss.biz.strategy.impl.MinioFileStrategy;

/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-06-25 10:59:53
 * @Description: 根据不同的策略注入到spring容器，确保只注册需要的
 */
@Configuration
@RefreshScope
public class FileStrategyFactory {
    @Value("${storage.type}")
    private String strategyType;

    @Bean
    @RefreshScope
    public FileStrategy getFileStrategy() {
        if (StringUtils.equals(strategyType, "minio")) {
            return new MinioFileStrategy();
        } else if (StringUtils.equals(strategyType, "aliyun")) {
            return new AliyunOSSFileStrategy();
        }

        throw new IllegalArgumentException("不可用的存储类型");
    }
}
