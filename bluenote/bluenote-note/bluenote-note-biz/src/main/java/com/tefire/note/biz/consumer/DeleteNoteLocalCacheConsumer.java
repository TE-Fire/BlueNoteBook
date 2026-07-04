/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-07-03 23:03:12
 * @Description: 
 */
package com.tefire.note.biz.consumer;

import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import com.tefire.note.biz.constant.MQConstants;
import com.tefire.note.biz.service.NoteService;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-07-03 23:03:12
 * @Description: 消费者订阅 Topic 为 TOPIC_DELETE_NOTE_LOCAL_CACHE
 */
@Component
@Slf4j
@RocketMQMessageListener(consumerGroup = "bluenote_group", //Group
        topic = MQConstants.TOPIC_DELETE_NOTE_LOCAL_CACHE, // 消费者的主题 Topic
        messageModel = MessageModel.BROADCASTING) // 广播模式
public class DeleteNoteLocalCacheConsumer implements RocketMQListener<String> {
    
    @Resource
    private NoteService noteService;

    @Override
    public void onMessage(String body) {
        Long noteId = Long.valueOf(body);
        log.info("## 消费者消费成功, noteId: {}", noteId);

        noteService.deleteNoteLocalCache(noteId);
    }
}
