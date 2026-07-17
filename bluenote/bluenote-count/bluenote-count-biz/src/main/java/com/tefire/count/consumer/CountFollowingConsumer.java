package com.tefire.count.consumer;

import java.util.Objects;

import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import com.tefire.count.constant.MQConstants;
import com.tefire.count.constant.RedisKeyConstants;
import com.tefire.count.enums.FollowUnfollowTypeEnum;
import com.tefire.count.model.dto.CountFollowUnfollowMqDTO;
import com.tefire.framework.common.util.JsonUtils;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-07-17 12:18:50
 * @Description: 计数：关注数
 */
@Component
@Slf4j
@RocketMQMessageListener(consumerGroup = "bluenote_group_" + MQConstants.TOPIC_COUNT_FOLLOWING,
    topic = MQConstants.TOPIC_COUNT_FOLLOWING
)
public class CountFollowingConsumer implements RocketMQListener<String> {

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Resource
    private RocketMQTemplate rocketMQTemplate;

    @Override
    public void onMessage(String body) {
        log.info("## 消费到了 MQ 【计数: 关注数】, {}...", body);

        if (StringUtils.isBlank(body)) return;

        // 关注数和粉丝数计数场景不同，单个用户无法短时间内关注大量用户，所以无需聚合
        // 直接对 Redis 中的 Hash 进行 +1 或 -1 操作即可
        CountFollowUnfollowMqDTO countFollowUnfollowMqDTO = JsonUtils.parseObject(body, CountFollowUnfollowMqDTO.class);

        Integer type = countFollowUnfollowMqDTO.getType();
        Long userId = countFollowUnfollowMqDTO.getUserId();

        String countUserKey = RedisKeyConstants.buildCountUserKey(userId);
        boolean isExisted = redisTemplate.hasKey(countUserKey);

        if (isExisted) {
            long count = Objects.equals(type, FollowUnfollowTypeEnum.FOLLOW.getCode()) ? 1 : -1;
            redisTemplate.opsForHash().increment(countUserKey, RedisKeyConstants.FIELD_FANS_TOTAL, count);
        }
            
         // 发送 MQ, 关注数写库
        // 构建消息对象
        Message<String> message = MessageBuilder.withPayload(body)
                .build();

        // 异步发送 MQ 消息
        rocketMQTemplate.asyncSend(MQConstants.TOPIC_COUNT_FOLLOWING_2_DB, message, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.info("==> 【计数服务：关注数入库】MQ 发送成功，SendResult: {}", sendResult);
            }

            @Override
            public void onException(Throwable throwable) {
                log.error("==> 【计数服务：关注数入库】MQ 发送异常: ", throwable);
            }
        });
    }
}
