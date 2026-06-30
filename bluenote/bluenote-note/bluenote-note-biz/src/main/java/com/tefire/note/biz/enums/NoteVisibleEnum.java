package com.tefire.note.biz.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-06-30 15:22:37
 * @Description: 笔记可见状态
 */
@Getter
@AllArgsConstructor
public enum NoteVisibleEnum {
    PUBLIC(0), // 公开，所有人可见
    PRIVATE(1); // 仅自己可见

    private final Integer code;
}
