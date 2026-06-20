package com.tefire.framework.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-06-20 14:35:13
 * @Description: 账号状态
 */
@Getter
@AllArgsConstructor
public enum StatusEnum {
    // 启用
    ENABLE(0),
    // 禁用
    DISABLED(1);

    private final Integer value;
}
