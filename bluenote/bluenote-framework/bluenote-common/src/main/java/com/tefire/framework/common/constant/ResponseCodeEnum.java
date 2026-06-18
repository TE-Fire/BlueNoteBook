package com.tefire.framework.common.constant;

import com.tefire.framework.common.exception.BaseExceptionInterface;

import lombok.Getter;

/**
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-06-19 00:01:18
 * @Description: 异常状态码
 */
@Getter
public enum ResponseCodeEnum implements BaseExceptionInterface {
    
    // ------------- 通用异常状态码 -------------
    SYSTEM_ERROR("AUTH-10000", "出错啦，后台小哥正在努力修复中..."),
    PARAM_NOT_VALID("AUTH-10001", "参数错误");
    
    // 异常码
    private final String errorCode;
    // 错误信息
    private final String errorMessage;
    
    // 构造函数
    ResponseCodeEnum(String errorCode, String errorMessage) {
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }
}