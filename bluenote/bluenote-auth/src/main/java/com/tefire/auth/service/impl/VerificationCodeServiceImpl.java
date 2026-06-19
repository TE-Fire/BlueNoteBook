package com.tefire.auth.service.impl;

import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.tefire.auth.constant.RedisKeyConstants;
import com.tefire.auth.enums.ResponseCodeEnum;
import com.tefire.auth.model.SendVerificationCodeReqVO;
import com.tefire.framework.common.exception.BizException;
import com.tefire.framework.common.response.Response;

import cn.hutool.core.util.RandomUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class VerificationCodeServiceImpl implements VerificationCodeService{

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Override
    public Response<?> send(SendVerificationCodeReqVO sendVerificationCodeReqVO) {

        String phone = sendVerificationCodeReqVO.getPhone();
        String key = RedisKeyConstants.buildVerificationCodeKey(phone);

        Boolean isSent = redisTemplate.hasKey(key);
        if (isSent) {
            // 之前获取的验证码未过期，提示发送频繁
            throw new BizException(ResponseCodeEnum.VERIFICATION_CODE_SEND_FREQUENTLY);
        }

        String verificationCode = RandomUtil.randomNumbers(6);

        // TODO: 调用第三方短信发送服务

        log.info("==> 手机号: {}, 已发送验证码：【{}】", phone, verificationCode);

        redisTemplate.opsForValue().set(key, verificationCode, 3, TimeUnit.MINUTES);

        return Response.success();
    }
}
