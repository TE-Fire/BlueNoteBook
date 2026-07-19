package com.tefire.count.consumer;

import java.time.Duration;
import java.util.List;

import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import com.github.phantomthief.collection.BufferTrigger;
import com.tefire.count.constant.MQConstants;
import com.tefire.framework.common.util.JsonUtils;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-07-19 22:06:31
 * @Description: 计数: 笔记点赞数
 */
@Component
@Slf4j
@RocketMQMessageListener(consumerGroup = "bluenote_group_" + MQConstants.TOPIC_COUNT_NOTE_LIKE,
    topic = MQConstants.TOPIC_COUNT_NOTE_LIKE
)
public class CountNoteLikeConsumer implements RocketMQListener<String> {
    
    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Resource
    private RocketMQTemplate rocketMQTemplate;

    private BufferTrigger<String> bufferTrigger = BufferTrigger.<String>batchBlocking()
                .bufferSize(50000) // 缓存队列的最大容量
                .batchSize(1000)   // 一批次最多聚合 1000 条
                .linger(Duration.ofSeconds(1)) // 多久聚合一次
                .setConsumerEx(this::consumeMessage) // 设置消费者方法
                .build();

    @Override
    public void onMessage(String body) {
        // 往 bufferTrigger 中添加元素
        bufferTrigger.enqueue(body);
    }

    private void consumeMessage(List<String> bodys) {
        log.info("==> 【笔记点赞数】聚合消息, size: {}", bodys.size());
        log.info("==> 【笔记点赞数】聚合消息, {}", JsonUtils.toJsonString(bodys));

        // TODO:
    }
}
