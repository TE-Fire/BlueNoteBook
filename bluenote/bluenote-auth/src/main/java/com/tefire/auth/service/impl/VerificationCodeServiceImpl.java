package com.tefire.auth.service.impl;

import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import com.tefire.auth.constant.RedisKeyConstants;
import com.tefire.auth.enums.ResponseCodeEnum;
import com.tefire.auth.model.SendVerificationCodeReqVO;
import com.tefire.auth.service.VerificationCodeService;
import com.tefire.auth.sms.AliyunSmsHelper;
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

    @Resource(name = "taskExecutor")
    private ThreadPoolTaskExecutor threadPoolTaskExecutor;

    @Resource
    private AliyunSmsHelper aliyunSmsHelper;
    
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

        // 调用第三方短信发送服务
       threadPoolTaskExecutor.submit(() -> {
            String signName = "速通互联验证码"; // 签名，个人测试签名无法修改
            String templateCode = "100001"; // 短信模板编码
            // 短信模板参数，code 表示要发送的验证码；min 表示验证码有时间时长，即 3 分钟
            String templateParam = String.format("{\"code\":\"%s\",\"min\":\"3\"}", verificationCode);
            aliyunSmsHelper.sendMessage(signName, templateCode, phone, templateParam);
        });


        log.info("==> 手机号: {}, 已发送验证码：【{}】", phone, verificationCode);

        redisTemplate.opsForValue().set(key, verificationCode, 3, TimeUnit.MINUTES);

        return Response.success();
    }
}
