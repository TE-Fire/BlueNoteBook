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
     * 小蓝书全局 ID 生成器 KEY
     */
    public static final String BLUENOTE_ID_GENERATOR_KEY = "bluenote_id_generator";

    /**
     * 用户角色数据 KEY 前缀
     */
    private static final String USER_ROLES_KEY_PREFIX = "user:roles:";

    /**
     * 构建验证码 KEY
     * @param phone
     * @return
     */
    public static String buildVerificationCodeKey(String phone) {
        return VERIFICATION_CODE_KEY_PREFIX + phone;
    }

    /**
     * 构建用户-角色 Key
     * @param phone
     * @return
     */
    public static String buildUserRoleKey(String phone) {
        return USER_ROLES_KEY_PREFIX + phone;
    }
}
