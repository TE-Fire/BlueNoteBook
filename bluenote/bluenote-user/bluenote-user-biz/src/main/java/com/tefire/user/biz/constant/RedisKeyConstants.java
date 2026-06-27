package com.tefire.user.biz.constant;

/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-06-19 14:32:55
 * @Description: 
 */
public class RedisKeyConstants {
   
    /**
     * 小蓝书全局 ID 生成器 KEY
     */
    public static final String BLUENOTE_ID_GENERATOR_KEY = "bluenote.id.generator";

    /**
     * 用户角色数据 KEY 前缀
     */
    private static final String USER_ROLES_KEY_PREFIX = "user:roles:";

     /**
     * 角色对应的权限集合 KEY 前缀
     */
    private static final String ROLE_PERMISSIONS_KEY_PREFIX = "role:permissions:";

    /**
     * 构建用户-角色 Key
     * @param phone
     * @return
     */
    public static String buildUserRoleKey(Long userId) {
        return USER_ROLES_KEY_PREFIX + userId;
    }

    /**
     * 构建角色对应的权限集合 KEY
     * @param roleId
     * @return
     */
    public static String buildRolePermissionsKey(String roleKey) {
        return ROLE_PERMISSIONS_KEY_PREFIX + roleKey;
    }
}
