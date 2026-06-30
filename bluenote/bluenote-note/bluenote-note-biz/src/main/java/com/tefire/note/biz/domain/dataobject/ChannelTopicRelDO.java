package com.tefire.note.biz.domain.dataobject;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ChannelTopicRelDO {
    private Long id;

    private Long channelId;

    private Long topicId;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

}