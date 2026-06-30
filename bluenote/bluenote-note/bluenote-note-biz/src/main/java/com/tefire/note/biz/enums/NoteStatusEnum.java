package com.tefire.note.biz.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-06-30 15:21:30
 * @Description: 笔记发布状态
 */
@Getter
@AllArgsConstructor
public enum NoteStatusEnum {
    BE_EXAMINE(0), // 待审核
    NORMAL(1), // 正常展示
    DELETED(2), // 被删除
    DOWNED(3), // 被下架
    ;

    private final Integer code;
}
