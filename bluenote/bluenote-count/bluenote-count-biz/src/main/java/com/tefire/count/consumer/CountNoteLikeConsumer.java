package com.tefire.count.consumer;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import com.github.phantomthief.collection.BufferTrigger;
import com.google.common.collect.Maps;
import com.tefire.count.constant.MQConstants;
import com.tefire.count.enums.LikeUnlikeNoteTypeEnum;
import com.tefire.count.model.dto.CountLikeUnlikeNoteMqDTO;
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

    @SuppressWarnings("null")
    private void consumeMessage(List<String> bodys) {
        log.info("==> 【笔记点赞数】聚合消息, size: {}", bodys.size());
        log.info("==> 【笔记点赞数】聚合消息, {}", JsonUtils.toJsonString(bodys));

        // List<String> 转 List<CountLikeUnlikeNoteMqDTO>
        List<CountLikeUnlikeNoteMqDTO> countLikeUnlikeNoteMqDTOs = bodys.stream()
                .map(body -> JsonUtils.parseObject(body, CountLikeUnlikeNoteMqDTO.class)).toList();

        // 按照笔记 ID 分组
        Map<Long, List<CountLikeUnlikeNoteMqDTO>> group = countLikeUnlikeNoteMqDTOs.stream()
                .collect(Collectors.groupingBy(CountLikeUnlikeNoteMqDTO::getNoteId));

        // 按组汇总数据，统计出最终的计数
        // key 为笔记 ID, value 为最终操作的计数
        Map<Long, Integer> countMap = Maps.newHashMap();

        for (Map.Entry<Long, List<CountLikeUnlikeNoteMqDTO>> entry : group.entrySet()) {
            List<CountLikeUnlikeNoteMqDTO> list = entry.getValue();
            // 最终的计数值，默认为 0
            int finalCount = 0;
            for (CountLikeUnlikeNoteMqDTO countLikeUnlikeNoteMqDTO : list) {
                Integer type = countLikeUnlikeNoteMqDTO.getType();
                LikeUnlikeNoteTypeEnum likeUnlikeNoteTypeEnum = LikeUnlikeNoteTypeEnum.valueOf(type);

                if (Objects.isNull(likeUnlikeNoteTypeEnum)) continue;

                switch (likeUnlikeNoteTypeEnum) {
                    case LIKE -> finalCount += 1;
                    case UNLIKE -> finalCount -= 1;
                }
            }
            countMap.put(entry.getKey(), finalCount);
        }
        log.info("## 【笔记点赞数】聚合后的计数数据: {}", JsonUtils.toJsonString(countMap));
    }
}
