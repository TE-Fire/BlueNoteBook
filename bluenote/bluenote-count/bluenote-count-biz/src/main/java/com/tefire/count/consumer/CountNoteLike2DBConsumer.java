package com.tefire.count.consumer;

import java.util.List;

import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.google.common.util.concurrent.RateLimiter;
import com.tefire.count.constant.MQConstants;
import com.tefire.count.domain.mapper.NoteCountDOMapper;
import com.tefire.count.domain.mapper.UserCountDOMapper;
import com.tefire.count.model.dto.AggregationCountLikeUnlikeNoteMqDTO;
import com.tefire.framework.common.util.JsonUtils;

import cn.hutool.core.collection.CollUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-07-20 11:43:01
 * @Description: 笔记点赞落库
 */
@Component
@Slf4j
@RocketMQMessageListener(consumerGroup = "bluenote_group_" + MQConstants.TOPIC_COUNT_NOTE_LIKE_2_DB,
    topic = MQConstants.TOPIC_COUNT_NOTE_LIKE_2_DB
)
public class CountNoteLike2DBConsumer implements RocketMQListener<String> {
    
    @Resource
    private NoteCountDOMapper noteCountDOMapper;

    @Resource
    private UserCountDOMapper userCountDOMapper;

    @Resource
    private TransactionTemplate transactionTemplate;

    // 每秒创建 500 个令牌
    private RateLimiter rateLimiter = RateLimiter.create(500);

    @SuppressWarnings("null")
    @Override
    public void onMessage(String body) {
        // 流量削峰：通过获取令牌，如果没有令牌可用，将阻塞，直到获得
        rateLimiter.acquire();

        log.info("## 消费到了 MQ 【计数: 笔记点赞数入库】, {}...", body);

        List<AggregationCountLikeUnlikeNoteMqDTO> countList = null;

        try {
            countList  = JsonUtils.parseList(body, AggregationCountLikeUnlikeNoteMqDTO.class);
        } catch (Exception e) {
            log.error("## 解析 JSON 字符串异常", e);
        }

        if (CollUtil.isNotEmpty(countList)) {
            // 判断数据库中 t_user_count 和 t_note_count 表，若笔记计数记录不存在，则插入；若记录已存在，则直接更新
            countList.forEach(item -> {
                Long creatorId = item.getCreatorId();
                Long noteId = item.getNoteId();
                Integer count = item.getCount();

                // 编程式事务，保证两条语句的原子性
                transactionTemplate.execute(status -> {
                    try {
                        noteCountDOMapper.insertOrUpdateLikeTotalByNoteId(count, noteId);
                        userCountDOMapper.insertOrUpdateLikeTotalByUserId(count, creatorId);
                        return true;
                    } catch (Exception e) {
                        status.setRollbackOnly(); // 标记事务为回滚
                        log.error("", e);
                    }
                    return false;
                });
            });
        }
    }
}
