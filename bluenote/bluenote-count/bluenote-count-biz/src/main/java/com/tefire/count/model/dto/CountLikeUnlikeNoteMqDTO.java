package com.tefire.count.model.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-07-20 11:15:54
 * @Description: 点赞、取消点赞笔记
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CountLikeUnlikeNoteMqDTO {
    
    private Long userId;

    private Long noteId;

    /**
     * 0: 取消点赞， 1：点赞
     */
    private Integer type;

    private LocalDateTime createTime;
}
