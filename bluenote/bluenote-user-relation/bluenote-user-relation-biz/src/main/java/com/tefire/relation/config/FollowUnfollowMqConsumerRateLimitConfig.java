package com.tefire.relation.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.google.common.util.concurrent.RateLimiter;

/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-07-08 13:25:56
 * @Description: 关注/取关消费令牌桶
 */
@Configuration
@RefreshScope
public class FollowUnfollowMqConsumerRateLimitConfig {
    
    @Value("${mq-consumer.follow-unfollow.rate-limit}")
    private double rateLimit;

    @Bean
    @RefreshScope
    public RateLimiter rateLimiter() {
        return RateLimiter.create(rateLimit);
    }

}
