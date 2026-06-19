package com.tefire.auth.constant;

/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-06-19 14:32:55
 * @Description: 
 */
public class RedisKeyConstants {
    /**
     * 验证码 KEY 前缀
     */
    private static final String VERIFICATION_CODE_KEY_PREFIX = "verification_code:";

    /**
     * 构建验证码 KEY
     * @param phone
     * @return
     */
    public static String buildVerificationCodeKey(String phone) {
        return VERIFICATION_CODE_KEY_PREFIX + phone;
    }
}
