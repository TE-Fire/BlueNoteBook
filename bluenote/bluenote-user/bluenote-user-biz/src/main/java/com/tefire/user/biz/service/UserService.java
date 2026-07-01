package com.tefire.user.biz.service;

import com.tefire.framework.common.response.Response;
import com.tefire.user.biz.model.vo.UpdateUserInfoReqVO;
import com.tefire.user.dto.req.FindUserByIdReqDTO;
import com.tefire.user.dto.req.FindUserByPhoneReqDTO;
import com.tefire.user.dto.req.RegisterUserReqDTO;
import com.tefire.user.dto.req.UpdateUserPasswordReqDTO;
import com.tefire.user.dto.resp.FindUserByIdRspDTO;
import com.tefire.user.dto.resp.FindUserByPhoneRspDTO;

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

     /**
     * 用户注册
     *
     * @param registerUserReqDTO
     * @return
     */
    Response<Long> register(RegisterUserReqDTO registerUserReqDTO);

     /**
     * 根据手机号查询用户信息
     *
     * @param findUserByPhoneReqDTO
     * @return
     */
    Response<FindUserByPhoneRspDTO> findByPhone(FindUserByPhoneReqDTO findUserByPhoneReqDTO);

    /**
     * 更新密码
     *
     * @param updateUserPasswordReqDTO
     * @return
     */
    Response<?> updatePassword(UpdateUserPasswordReqDTO updateUserPasswordReqDTO);

    /**
     * 根据用户 ID 查询用户信息
     *
     * @param findUserByIdReqDTO
     * @return
     */
    Response<FindUserByIdRspDTO> findById(FindUserByIdReqDTO findUserByIdReqDTO);
}
