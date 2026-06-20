/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-06-20 13:55:59
 * @Description: 
 */
package com.tefire.auth.service;

import com.tefire.auth.model.vo.user.UserLoginReqVO;
import com.tefire.framework.common.response.Response;
public interface UserService {
    /**
     * 登录与注册
     * @param userLoginReqVO
     * @return
     */
    Response<String> loginAndRegister(UserLoginReqVO userLoginReqVO);
}
