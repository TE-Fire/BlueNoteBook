package com.tefire.auth.controller;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tefire.auth.model.vo.user.UserLoginReqVO;
import com.tefire.auth.service.UserService;
import com.tefire.framework.biz.operationlog.aspect.ApiOperationLog;
import com.tefire.framework.common.response.Response;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-06-20 14:23:56
 * @Description: 登录注册
 */
@RestController
@RequestMapping("/user")
@Slf4j
public class UserController {
    
    @Resource
    private UserService userService;
    
    @PostMapping("/login")
    @ApiOperationLog(description = "用户登录/注册")
    public Response<String> loginAndRegister(@Validated @RequestBody UserLoginReqVO userLoginReqVO) {
        return userService.loginAndRegister(userLoginReqVO);
    }
}
