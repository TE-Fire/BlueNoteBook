package com.tefire.note.biz.consumer;

import java.time.LocalDateTime;
import java.util.Objects;

import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import com.google.common.util.concurrent.RateLimiter;
import com.tefire.framework.common.util.JsonUtils;
import com.tefire.note.biz.constant.MQConstants;
import com.tefire.note.biz.domain.dataobject.NoteCollectionDO;
import com.tefire.note.biz.domain.mapper.NoteCollectionDOMapper;
import com.tefire.note.biz.model.dto.CollectUnCollectNoteMqDTO;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-07-20 21:50:40
 * @Description: 笔记收藏、取消收藏 MQ 消费者
 */
@Component
@Slf4j
@RocketMQMessageListener(consumerGroup = "bluenote_group_" + MQConstants.TOPIC_COLLECT_OR_UN_COLLECT,
    topic = MQConstants.TOPIC_COLLECT_OR_UN_COLLECT,
    consumeMode = ConsumeMode.ORDERLY
)
public class CollectUnCollectNoteConsumer implements RocketMQListener<Message> {
    
    @Resource
    private NoteCollectionDOMapper noteCollectionDOMapper;
    
    // 每秒创建 300 个令牌
    private RateLimiter rateLimiter = RateLimiter.create(300);

    @Override
    public void onMessage(Message message) {
        // 流量削峰：通过获取令牌，如果没有令牌可用，将阻塞，直到获得
        rateLimiter.acquire();

        // 幂等性: 通过联合唯一索引保证

        // 消息体
        String bodyJsonStr = new String(message.getBody());
        String tags = message.getTags();

         log.info("==> CollectUnCollectNoteConsumer 消费了消息 {}, tags: {}", bodyJsonStr, tags);

        // 根据 MQ 标签，判断操作类型
        if (Objects.equals(tags, MQConstants.TAG_COLLECT)) { // 收藏笔记
            handleCollectNoteTagMessage(bodyJsonStr);
        } else if (Objects.equals(tags, MQConstants.TAG_UN_COLLECT)) { // 取消收藏笔记
            handleUnCollectNoteTagMessage(bodyJsonStr);
        }
    }

     /**
     * 笔记收藏
     * @param bodyJsonStr
     */
    private void handleCollectNoteTagMessage(String bodyJsonStr) {
        CollectUnCollectNoteMqDTO collectUnCollectNoteMqDTO = JsonUtils.parseObject(bodyJsonStr, CollectUnCollectNoteMqDTO.class);
        if (Objects.isNull(collectUnCollectNoteMqDTO)) return;

        // 用户ID
        Long userId = collectUnCollectNoteMqDTO.getUserId();
        // 收藏的笔记ID
        Long noteId = collectUnCollectNoteMqDTO.getNoteId();
        // 操作类型
        Integer type = collectUnCollectNoteMqDTO.getType();
        // 收藏时间
        LocalDateTime createTime = collectUnCollectNoteMqDTO.getCreateTime();

        // 构建 DO 对象
        NoteCollectionDO noteCollectionDO = NoteCollectionDO.builder()
                .userId(userId)
                .noteId(noteId)
                .createTime(createTime)
                .status(type)
                .build();

        // 添加或更新笔记收藏记录
        int count = noteCollectionDOMapper.insertOrUpdate(noteCollectionDO);

        // TODO: 发送计数 MQ
    }

    /**
     * 笔记取消收藏
     * @param bodyJsonStr
     */
    private void handleUnCollectNoteTagMessage(String bodyJsonStr) {
    }

}
