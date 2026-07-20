package com.tefire.note.biz.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-07-20 21:42:59
 * @Description: 笔记收藏、取消收藏 Type
 */
@Getter
@AllArgsConstructor
public enum CollectUnCollectNoteTypeEnum {
    
    // 收藏
    COLLECT(1),
    // 取消收藏
    UN_COLLECT(0),
    ;

    private final Integer code;
}
