package com.tefire.auth.service.impl;

import com.tefire.auth.model.SendVerificationCodeReqVO;
import com.tefire.framework.common.response.Response;

public interface VerificationCodeService {
    /**
     * 发送短信验证码
     *
     * @param sendVerificationCodeReqVO
     * @return
     */
    Response<?> send(SendVerificationCodeReqVO sendVerificationCodeReqVO);
}
