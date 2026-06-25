package com.tefire.user.biz.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
import java.util.Objects;

/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-06-25 22:06:33
 * @Description: 性别枚举类
 */
@Getter
@AllArgsConstructor
public enum SexEnum {
    WOMAN(0),
    MAN(1);

    private final Integer value;

    public static boolean isValid(Integer value) {
        for (SexEnum loginTypeEnum : SexEnum.values()) {
            if (Objects.equals(value, loginTypeEnum.getValue())) {
                return true;
            }
        }
        return false;
    }
}
