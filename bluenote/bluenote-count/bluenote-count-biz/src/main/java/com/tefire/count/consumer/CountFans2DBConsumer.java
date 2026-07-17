package com.tefire.count.consumer;

import java.util.Map;

import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import com.google.common.util.concurrent.RateLimiter;
import com.tefire.count.constant.MQConstants;
import com.tefire.count.domain.mapper.UserCountDOMapper;
import com.tefire.framework.common.util.JsonUtils;

import cn.hutool.core.collection.CollUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-07-17 19:30:22
 * @Description: 计数: 粉丝数存库
 */
@Component
@Slf4j
@RocketMQMessageListener(consumerGroup = "bluenote_group_" + MQConstants.TOPIC_COUNT_FANS_2_DB,
    topic = MQConstants.TOPIC_COUNT_FANS_2_DB
)
public class CountFans2DBConsumer implements RocketMQListener<String> {

    @Resource
    private UserCountDOMapper userCountDOMapper;

    // 每秒创建 500 个令牌
    private RateLimiter rateLimiter = RateLimiter.create(500);

    @SuppressWarnings("null")
    @Override
    public void onMessage(String body) {
        // 流量削峰：通过获取令牌，如果没有令牌可用，将阻塞，直到获得
        rateLimiter.acquire();
        log.info("## 消费到了 MQ 【计数: 粉丝数入库】, {}...", body);

        Map<Long, Integer> map = null;

        try {
            map = JsonUtils.parseMap(body, Long.class, Integer.class);
        } catch (Exception e) {
            log.error("## 解析 JSON 字符串异常", e);
        }

        if (CollUtil.isNotEmpty(map)) {
            // 判断数据库中，若目标用户的记录不存在，则插入；若记录已存在，则直接更新
            map.forEach((k, v) -> userCountDOMapper.insertOrUpdateFansTotalByUserId(v, k));
        }
    }

}
