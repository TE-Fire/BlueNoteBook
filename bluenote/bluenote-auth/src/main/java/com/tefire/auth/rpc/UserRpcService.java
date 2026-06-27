package com.tefire.auth.rpc;

import org.springframework.stereotype.Component;

import com.tefire.framework.common.response.Response;
import com.tefire.user.api.UserFeignApi;
import com.tefire.user.dto.req.RegisterUserReqDTO;

import jakarta.annotation.Resource;

@Component
public class UserRpcService {
     @Resource
    private UserFeignApi userFeignApi;

    /**
     * 用户注册
     *
     * @param phone
     * @return
     */
    public Long registerUser(String phone) {
        RegisterUserReqDTO registerUserReqDTO = new RegisterUserReqDTO();
        registerUserReqDTO.setPhone(phone);

        Response<Long> response = userFeignApi.registerUser(registerUserReqDTO);

        if (!response.isSuccess()) {
            return null;
        }

        return response.getData();
    }
}
