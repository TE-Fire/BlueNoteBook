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
import com.tefire.note.biz.domain.dataobject.NoteLikeDO;
import com.tefire.note.biz.domain.mapper.NoteLikeDOMapper;
import com.tefire.note.biz.model.dto.LikeUnlikeNoteMqDTO;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-07-19 10:47:39
 * @Description: 笔记点赞、取消点赞落库
 */
@Component
@Slf4j
@RocketMQMessageListener(consumerGroup = "bluenote_group_" + MQConstants.TOPIC_LIKE_OR_UNLIKE,
    topic = MQConstants.TOPIC_LIKE_OR_UNLIKE,
    consumeMode = ConsumeMode.ORDERLY
)
public class LikeUnlikeNoteConsumer implements RocketMQListener<Message> {

    @Resource
    private NoteLikeDOMapper noteLikeDOMapper;

    // 每秒创建 500 个令牌
    private RateLimiter rateLimiter = RateLimiter.create(500);

    @Override
    public void onMessage(Message messag) {
        // 流量削峰：通过获取令牌，如果没有令牌可用，将阻塞，直到获得
        rateLimiter.acquire();

        // 幂等性: 通过联合唯一索引保证

        String bodyJsonStr = new String(messag.getBody());
        String tags = messag.getTags();

        log.info("==> LikeUnlikeNoteConsumer 消费了消息 {}, tags: {}", bodyJsonStr, tags);

        if (Objects.equals(tags, MQConstants.TAG_LIKE)) {
            handleLikeNoteTagMessage(bodyJsonStr);
        } else if (Objects.equals(tags, MQConstants.TAG_UNLIKE)) {
            handleUnlikeNoteTagMessage(bodyJsonStr);
        }

    }

     /**
     * 笔记点赞
     * @param bodyJsonStr
     */
    private void handleLikeNoteTagMessage(String bodyJsonStr) {
        // 消息体 JSON 字符串转 DTO
        LikeUnlikeNoteMqDTO likeNoteMqDTO = JsonUtils.parseObject(bodyJsonStr, LikeUnlikeNoteMqDTO.class);

        if (Objects.isNull(likeNoteMqDTO)) return;

        // 用户ID
        Long userId = likeNoteMqDTO.getUserId();
        // 点赞的笔记ID
        Long noteId = likeNoteMqDTO.getNoteId();
        // 操作类型
        Integer type = likeNoteMqDTO.getType();
        // 点赞时间
        LocalDateTime createTime = likeNoteMqDTO.getCreateTime();

        // 构建 DO 对象
        NoteLikeDO noteLikeDO = NoteLikeDO.builder()
                .userId(userId)
                .noteId(noteId)
                .createTime(createTime)
                .status(type)
                .build();

        // 添加或更新笔记点赞记录
        int count = noteLikeDOMapper.insertOrUpdate(noteLikeDO);

        // TODO: 发送计数 MQ
    }

    /**
     * 笔记取消点赞
     * @param bodyJsonStr
     */
    private void handleUnlikeNoteTagMessage(String bodyJsonStr) {
        // 消息体 JSON 字符串转 DTO
        LikeUnlikeNoteMqDTO unlikeNoteMqDTO = JsonUtils.parseObject(bodyJsonStr, LikeUnlikeNoteMqDTO.class);

        if (Objects.isNull(unlikeNoteMqDTO)) return;

        // 用户ID
        Long userId = unlikeNoteMqDTO.getUserId();
        // 点赞的笔记ID
        Long noteId = unlikeNoteMqDTO.getNoteId();
        // 操作类型
        Integer type = unlikeNoteMqDTO.getType();
        // 点赞时间
        LocalDateTime createTime = unlikeNoteMqDTO.getCreateTime();

        // 构建 DO 对象
        NoteLikeDO noteLikeDO = NoteLikeDO.builder()
                .userId(userId)
                .noteId(noteId)
                .createTime(createTime)
                .status(type)
                .build();

        // 取消点赞：记录更新
        int count = noteLikeDOMapper.update2UnlikeByUserIdAndNoteId(noteLikeDO);

        // TODO: 发送计数 MQ
    }
}
