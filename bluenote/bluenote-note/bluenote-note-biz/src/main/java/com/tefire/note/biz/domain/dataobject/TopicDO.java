package com.tefire.note.biz.domain.dataobject;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-06-30 15:13:09
 * @Description: 话题
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TopicDO {
    private Long id;

    private String name;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Boolean isDeleted;

}