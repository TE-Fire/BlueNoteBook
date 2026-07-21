package com.tefire.count.consumer;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import com.github.phantomthief.collection.BufferTrigger;
import com.google.common.collect.Lists;
import com.tefire.count.constant.MQConstants;
import com.tefire.count.constant.RedisKeyConstants;
import com.tefire.count.enums.CollectUnCollectNoteTypeEnum;
import com.tefire.count.model.dto.AggregationCountCollectUnCollectNoteMqDTO;
import com.tefire.count.model.dto.CountCollectUnCollectNoteMqDTO;
import com.tefire.framework.common.util.JsonUtils;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-07-21 10:53:13
 * @Description: 笔记收藏：聚合计数（笔记维度）
 */
@Component
@Slf4j
@RocketMQMessageListener(consumerGroup = "bluenote_group_" + MQConstants.TOPIC_COUNT_NOTE_COLLECT,
    topic = MQConstants.TOPIC_COUNT_NOTE_COLLECT
)
public class CountNoteCollectConsumer implements RocketMQListener<String> {
    
    @Resource
    private RedisTemplate<String, Object> redisTemplate;
    @Resource
    private RocketMQTemplate rocketMQTemplate;

    private BufferTrigger<String> bufferTrigger = BufferTrigger.<String>batchBlocking()
            .bufferSize(1000) // 缓存队列的最大容量
            .batchSize(1000)   // 一批次最多聚合 1000 条
            .linger(Duration.ofSeconds(1)) // 多久聚合一次
            .setConsumerEx(this::consumeMessage) // 设置消费者方法
            .build();

    @Override
    public void onMessage(String body) {
        // 往 bufferTrigger 中添加元素
        bufferTrigger.enqueue(body);
    }

    @SuppressWarnings("null")
    private void consumeMessage(List<String> bodys) {
        log.info("==> 【笔记收藏数】聚合消息, size: {}", bodys.size());
        log.info("==> 【笔记收藏数】聚合消息, {}", JsonUtils.toJsonString(bodys));

        // List<String> 转 List<CountCollectUnCollectNoteMqDTO>
        List<CountCollectUnCollectNoteMqDTO> collectNoteMqDTOs = bodys.stream()
                .map(body -> JsonUtils.parseObject(body, CountCollectUnCollectNoteMqDTO.class))
                .toList();
        // 按笔记 id 分组
        Map<Long, List<CountCollectUnCollectNoteMqDTO>> groupMap = collectNoteMqDTOs.stream()
                .collect(Collectors.groupingBy(CountCollectUnCollectNoteMqDTO::getNoteId));

        // 按组汇总数据，统计出最终的计数
        // key 为笔记 ID, value 为最终操作的计数
        List<AggregationCountCollectUnCollectNoteMqDTO> countList = Lists.newArrayList();

        for (Map.Entry<Long, List<CountCollectUnCollectNoteMqDTO>> entry : groupMap.entrySet()) {
            Long noteId = entry.getKey();
            Long creatorId = null;

            List<CountCollectUnCollectNoteMqDTO> list = entry.getValue();
            // 最终的计数值，默认为 0
            int finalCount = 0;

            for (CountCollectUnCollectNoteMqDTO collectNoteMqDTO : list) {
                creatorId = collectNoteMqDTO.getNoteCreatorId();
                // 获取操作类型
                Integer type = collectNoteMqDTO.getType();
                CollectUnCollectNoteTypeEnum collectNoteTypeEnum = CollectUnCollectNoteTypeEnum.valueOf(type);

                switch (collectNoteTypeEnum) {
                    case COLLECT -> finalCount += 1;
                    case UN_COLLECT -> finalCount -= 1;
                }
            }
            // 将分组后统计出的最终计数，存入 countList 中
            countList.add(AggregationCountCollectUnCollectNoteMqDTO.builder()
                            .noteId(noteId)
                            .creatorId(creatorId)
                            .count(finalCount)
                            .build());
        }
        log.info("## 【笔记收藏数】聚合后的计数数据: {}", JsonUtils.toJsonString(countList));

        // 更新 redis
        countList.forEach(item -> {
            Long creatorId = item.getCreatorId();
            Long noteId = item.getNoteId();
            Integer count = item.getCount();
            
            String countNoteRedisKey = RedisKeyConstants.buildCountNoteKey(noteId);
            // 判断 Redis 中 Hash 是否存在
            boolean isExisted = redisTemplate.hasKey(countNoteRedisKey);

            // 若存在才会更新
            // (因为缓存设有过期时间，考虑到过期后，缓存会被删除，这里需要判断一下，存在才会去更新，而初始化工作放在查询计数来做)
            if (isExisted) {
                // 对目标用户 Hash 中的收藏总数字段进行计数操作
                redisTemplate.opsForHash().increment(countNoteRedisKey, RedisKeyConstants.FIELD_COLLECT_TOTAL, count);
            }

            String countUserRedisKey = RedisKeyConstants.buildCountUserKey(creatorId);
            boolean isCountUserExisted = redisTemplate.hasKey(countUserRedisKey);
            if (isCountUserExisted) {
                redisTemplate.opsForHash().increment(countUserRedisKey, RedisKeyConstants.FIELD_COLLECT_TOTAL, count);
            }
        });

        // 发送 MQ, 笔记收藏数据落库
        Message<String> message = MessageBuilder.withPayload(JsonUtils.toJsonString(countList))
                .build();

        // 异步发送 MQ 消息
        rocketMQTemplate.asyncSend(MQConstants.TOPIC_COUNT_NOTE_COLLECT_2_DB, message, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.info("==> 【计数服务：笔记收藏数入库】MQ 发送成功，SendResult: {}", sendResult);
            }

            @Override
            public void onException(Throwable throwable) {
                log.error("==> 【计数服务：笔记收藏数入库】MQ 发送异常: ", throwable);
            }
        });
    }
}
