
package com.tefire.auth.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tefire.auth.model.SendVerificationCodeReqVO;
import com.tefire.auth.service.impl.VerificationCodeService;
import com.tefire.framework.biz.operationlog.aspect.ApiOperationLog;
import com.tefire.framework.common.response.Response;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;


/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-06-19 14:38:01
 * @Description: 
 */
@Slf4j
@RestController
@RequestMapping("/verification")
public class VerificationCodeController {
    
    @Resource
    private VerificationCodeService verificationCodeService;

    @PostMapping("/code/send")
    @ApiOperationLog(description = "发送短信验证码")
    public Response<?> send(@RequestBody @Validated SendVerificationCodeReqVO sendVerificationCodeReqVO) {
        return verificationCodeService.send(sendVerificationCodeReqVO);
    }
    
}
