package com.tefire.count.enums;

import java.util.Objects;

import lombok.AllArgsConstructor;
import lombok.Getter;

/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-07-21 11:02:12
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

    public static CollectUnCollectNoteTypeEnum valueOf(Integer code) {
        for (CollectUnCollectNoteTypeEnum collectUnCollectNoteTypeEnum : CollectUnCollectNoteTypeEnum.values()) {
            if (Objects.equals(code, collectUnCollectNoteTypeEnum.getCode())) {
                return collectUnCollectNoteTypeEnum;
            }
        }
        return null;
    }
}
