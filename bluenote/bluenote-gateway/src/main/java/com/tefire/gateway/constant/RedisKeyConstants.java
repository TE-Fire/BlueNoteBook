package com.tefire.gateway.constant;

/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-06-23 13:05:13
 * @Description: 用户-角色 角色-权限（key-value关系构建）
 */
public class RedisKeyConstants {

    /**
     * 用户对应角色集合 KEY 前缀
     */
    private static final String USER_ROLES_KEY_PREFIX = "user:roles:";

    /**
     * 角色对应的权限集合 KEY 前缀
     */
    private static final String ROLE_PERMISSIONS_KEY_PREFIX = "role:permissions:";

    /**
     * 构建角色对应的权限集合 KEY
     * @param roleKey
     * @return
     */
    public static String buildRolePermissionsKey(String roleKey) {
        return ROLE_PERMISSIONS_KEY_PREFIX + roleKey;
    }

    /**
     * 构建用户-角色 KEY
     * @param userId
     * @return
     */
    public static String buildUserRoleKey(Long userId) {
        return USER_ROLES_KEY_PREFIX + userId;
    }
}

