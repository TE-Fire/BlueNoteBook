package com.tefire.framework.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-06-20 14:33:24
 * @Description: 逻辑删除
 */
@Getter
@AllArgsConstructor
public enum DeletedEnum {
    YES(true),
    NO(false);

    private final Boolean value;
}
