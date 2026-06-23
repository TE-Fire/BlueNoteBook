package com.tefire.auth.filter;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.tefire.framework.common.constant.GlobalConstants;

/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-06-23 20:50:18
 * @Description: 登录用户上下文
 */
public class LoginUserContextHolder {
    
    private static final ThreadLocal<Map<String, Object>> LOGIN_USER_CONTEXT_THREAD_LOCAL
        = ThreadLocal.withInitial(HashMap::new);


    /**
     * 设置用户 ID
     * @param value
     */
    public static void setUserId(Object value) {
        LOGIN_USER_CONTEXT_THREAD_LOCAL.get().put(GlobalConstants.USER_ID, value);
    }

    /**
     * 获取用户 ID
     *
     * @return
     */
    public static Long getUserId() {
        Object value = LOGIN_USER_CONTEXT_THREAD_LOCAL.get().get(GlobalConstants.USER_ID);
        if (Objects.isNull(value)) {
            return null;
        }
        return Long.valueOf(value.toString());
    }

    /**
     * 删除 ThreadLocal 
     */
    public static void remove() {
        LOGIN_USER_CONTEXT_THREAD_LOCAL.remove();
    }
}
