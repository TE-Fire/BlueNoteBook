package com.tefire.auth.service;

import com.tefire.auth.model.vo.user.UpdatePasswordReqVO;
import com.tefire.auth.model.vo.user.UserLoginReqVO;
import com.tefire.framework.common.response.Response;

public interface UserService {
    /**
     * 登录与注册
     * @param userLoginReqVO
     * @return
     */
    Response<String> loginAndRegister(UserLoginReqVO userLoginReqVO);

    /**
     * 退出登录
     * @return
     */
    Response<?> logout();

    /**
     * 修改密码
     * @param updatePasswordReqVO
     * @return
     */
    Response<?> updatePassword(UpdatePasswordReqVO updatePasswordReqVO);
}
