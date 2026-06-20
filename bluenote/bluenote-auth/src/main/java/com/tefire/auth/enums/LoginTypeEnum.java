package com.tefire.auth.enums;

import java.util.Objects;

import lombok.AllArgsConstructor;
import lombok.Getter;

/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-06-20 13:51:11
 * @Description: 登录类型
 */
@Getter
@AllArgsConstructor
public enum LoginTypeEnum {
       // 验证码
    VERIFICATION_CODE(1),
    // 密码
    PASSWORD(2);

    private final Integer value;

    public static LoginTypeEnum valueOf(Integer code) {
        for (LoginTypeEnum loginTypeEnum : LoginTypeEnum.values()) {
            if (Objects.equals(code, loginTypeEnum.getValue())) {
                return loginTypeEnum;
            }
        }
        return null;
    }
}
