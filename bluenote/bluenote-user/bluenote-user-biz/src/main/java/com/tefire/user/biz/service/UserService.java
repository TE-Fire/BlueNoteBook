package com.tefire.user.biz.service;

import com.tefire.framework.common.response.Response;
import com.tefire.user.biz.model.vo.UpdateUserInfoReqVO;

/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-06-25 22:10:12
 * @Description: 用户服务业务接口
 */
public interface UserService {
    /**
     * 更新用户信息
     *
     * @param updateUserInfoReqVO
     * @return
     */
    Response<?> updateUserInfo(UpdateUserInfoReqVO updateUserInfoReqVO);
}
