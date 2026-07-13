package com.tefire.relation.consumer;

import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.tefire.framework.common.util.DateUtils;
import com.tefire.framework.common.util.JsonUtils;
import com.tefire.relation.config.FollowUnfollowMqConsumerRateLimitConfig;
import com.tefire.relation.constant.MQConstants;
import com.tefire.relation.constant.RedisKeyConstants;
import com.tefire.relation.domain.dataobject.FansDO;
import com.tefire.relation.domain.dataobject.FollowingDO;
import com.tefire.relation.domain.mapper.FansDOMapper;
import com.tefire.relation.domain.mapper.FollowingDOMapper;
import com.tefire.relation.model.dto.FollowUserMqDTO;
import com.tefire.relation.model.dto.UnfollowUserMqDTO;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Objects;

import org.apache.rocketmq.common.message.Message;

/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-07-08 09:52:31
 * @Description: 关注、取关消费者
 */
@Component
@Slf4j
@RocketMQMessageListener(consumerGroup = "bluenote_group",
        topic = MQConstants.TOPIC_FOLLOW_OR_UNFOLLOW
)
public class FollowUnfollowConsumer implements RocketMQListener<Message> {
    
    @Resource
    private FollowingDOMapper followingDOMapper;

    @Resource
    private FansDOMapper fansDOMapper;

    @Resource
    private TransactionTemplate transactionTemplate;

    @Resource
    private FollowUnfollowMqConsumerRateLimitConfig rateLimitConfig;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Override
    public void onMessage(Message message) {
        // 流量削峰：通过获取令牌，如果没有令牌可用，将阻塞，直到获得
        rateLimitConfig.rateLimiter();
        
        String bodyJsonStr = new String(message.getBody());
        String tags = message.getTags();

        log.info("==> FollowUnfollowConsumer 消费了消息 {}, tags: {}", bodyJsonStr, tags);

        if (Objects.equals(tags, MQConstants.TAG_FOLLOW)) {
            handleFollowTagMessage(bodyJsonStr);
        } else if (Objects.equals(tags, MQConstants.TAG_UNFOLLOW)) {
            handleUnfollowTagMessage(bodyJsonStr);
        }
    }

    /**
     * 关注
     * @param bodyJsonStr
     */
    private void handleFollowTagMessage(String bodyJsonStr) {
        FollowUserMqDTO followUserMqDTO = JsonUtils.parseObject(bodyJsonStr, FollowUserMqDTO.class);

        if (Objects.isNull(followUserMqDTO)) {
            return;
        }

        // 幂等性：通过联合唯一索引保证
        Long userId = followUserMqDTO.getUserId();
        Long followUserId = followUserMqDTO.getFollowUserId();
        LocalDateTime createTime = followUserMqDTO.getCreateTime();

        // 编程式提交事务
        boolean isSuccess = Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            try {
                int count = followingDOMapper.insert(FollowingDO.builder()
                        .userId(userId)
                        .followingUserId(followUserId)
                        .createTime(createTime)
                        .build());

                if (count > 0) {
                    fansDOMapper.insert(FansDO.builder()
                        .userId(followUserId)
                        .fansUserId(userId)
                        .createTime(createTime)
                        .build());
                }
                
                return true;        
            } catch (Exception e) {
                status.setRollbackOnly(); // 标记为事务回滚
                log.error("", e);
                return false;
            }
        }));
        log.info("## 数据库添加记录结果：{}", isSuccess);
        // 更新 Redis 中被关注用户的 ZSet 粉丝列表
        if (isSuccess) {
            DefaultRedisScript<Long> script = new DefaultRedisScript<>();
            script.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/follow_check_and_update_fans_zset.lua")));
            script.setResultType(Long.class);

            long timestamp = DateUtils.localDateTime2Timestamp(createTime);

            String userFansKey = RedisKeyConstants.buildUserFansKey(userId);
            redisTemplate.execute(script, Collections.singletonList(userFansKey), userId, timestamp);
        }
    }

    private void handleUnfollowTagMessage(String bodyJsonStr) {
        // 将消息体 Json 字符串转为 DTO 对象
        UnfollowUserMqDTO unfollowUserMqDTO = JsonUtils.parseObject(bodyJsonStr, UnfollowUserMqDTO.class);

        // 判空
        if (Objects.isNull(unfollowUserMqDTO)) return;

        Long userId = unfollowUserMqDTO.getUserId();
        Long unfollowUserId = unfollowUserMqDTO.getUnfollowUserId();

        // 编程式提交事务
        boolean isSuccess = Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            try {
                // 取关成功需要删除数据库两条记录
                // 关注表：一条记录
                int count = followingDOMapper.deleteByUserIdAndFollowingUserId(userId, unfollowUserId);

                // 粉丝表：一条记录
                if (count > 0) {
                    fansDOMapper.deleteByUserIdAndFansUserId(unfollowUserId, userId);
                }
                return true;
            } catch (Exception ex) {
                status.setRollbackOnly(); // 标记事务为回滚
                log.error("", ex);
            }
            return false;
        }));

        // 若数据库删除成功，更新 Redis，将自己从被取注用户的 ZSet 粉丝列表删除
        if (isSuccess) {
            // 被取关用户的粉丝列表 Redis Key
            String fansRedisKey = RedisKeyConstants.buildUserFansKey(unfollowUserId);
            // 删除指定粉丝
            redisTemplate.opsForZSet().remove(fansRedisKey, userId);
        }
    }
}
