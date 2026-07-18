package com.tefire.note.biz.enums;

import java.util.Objects;

import lombok.AllArgsConstructor;
import lombok.Getter;

/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-07-18 21:41:24
 * @Description: 笔记点赞：执行 Lua 脚本返回结果
 */
@Getter
@AllArgsConstructor
public enum NoteLikeLuaResultEnum {
    
    // 布隆过滤器不存在
    BLOOM_NOT_EXIST(-1L),
    // 笔记已点赞
    NOTE_LIKED(1L),
    ;

    private final Long code;

    /**
     * 根据类型 code 获取对应的枚举
     *
     * @param code
     * @return
     */
    public static NoteLikeLuaResultEnum valueOf(Long code) {
        for (NoteLikeLuaResultEnum noteLikeLuaResultEnum : NoteLikeLuaResultEnum.values()) {
            if (Objects.equals(code, noteLikeLuaResultEnum.getCode())) {
                return noteLikeLuaResultEnum;
            }
        }
        return null;
    }
}
