package com.tefire.relation.constant;

/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-07-07 15:21:36
 * @Description: 用户关系
 */
public class RedisKeyConstants {

    /**
     * 关注列表 KEY 前缀
     */
    private static final String USER_FOLLOWING_KEY_PREFIX = "following:";

    /**
     * 构建关注列表完整的 KEY
     * @param userId
     * @return
     */
    public static String buildUserFollowingKey(Long userId) {
        return USER_FOLLOWING_KEY_PREFIX + userId;
    }

}
