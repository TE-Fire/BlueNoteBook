package com.tefire.count.consumer;

import java.time.Duration;
import java.util.List;

import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import com.github.phantomthief.collection.BufferTrigger;
import com.tefire.count.constant.MQConstants;
import com.tefire.framework.common.util.JsonUtils;

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
    
    private BufferTrigger<String> bufferTrigger = BufferTrigger.<String>batchBlocking()
            .bufferSize(50000) // 缓存队列的最大容量
            .batchSize(1000)   // 一批次最多聚合 1000 条
            .linger(Duration.ofSeconds(1)) // 多久聚合一次
            .setConsumerEx(this::consumeMessage)
            .build();

    @Override
    public void onMessage(String body) {
        log.info("## 消费到了 MQ 【计数: 粉丝数】, {}...", body);
        // 往 bufferTrigger 中添加元素
        bufferTrigger.enqueue(body);

    }

    private void consumeMessage(List<String> bodys) {
        log.info("==> 聚合消息, size: {}", bodys.size());
        log.info("==> 聚合消息, {}", JsonUtils.toJsonString(bodys));
    }
}
