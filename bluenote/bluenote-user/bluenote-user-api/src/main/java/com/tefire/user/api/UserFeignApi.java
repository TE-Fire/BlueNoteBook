package com.tefire.user.api;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.tefire.framework.common.response.Response;
import com.tefire.user.constant.ApiConstants;
import com.tefire.user.dto.req.RegisterUserReqDTO;

/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-06-26 22:44:35
 * @Description: 用户服务调用feign
 */
@FeignClient(name = ApiConstants.SERVICE_NAME)
public interface UserFeignApi {
    
    String PREFIX = "/user";

    /**
     * 用户注册
     *
     * @param registerUserReqDTO
     * @return
     */
    @PostMapping(value = PREFIX + "/register")
    Response<Long> registerUser(@RequestBody RegisterUserReqDTO registerUserReqDTO);
}
