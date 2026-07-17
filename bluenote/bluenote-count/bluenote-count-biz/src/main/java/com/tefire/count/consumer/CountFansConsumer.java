package com.tefire.count.consumer;

import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import com.tefire.count.constant.MQConstants;

import lombok.extern.slf4j.Slf4j;

/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-07-17 12:29:49
 * @Description: 粉丝数计数
 */
@Component
@Slf4j
@RocketMQMessageListener(consumerGroup = "bluenote_" + MQConstants.TOPIC_COUNT_FANS,
    topic = MQConstants.TOPIC_COUNT_FANS
)
public class CountFansConsumer implements RocketMQListener<String> {
    
    @Override
    public void onMessage(String body) {
        log.info("## 消费到了 MQ 【计数: 粉丝数】, {}...", body);
    }
}
