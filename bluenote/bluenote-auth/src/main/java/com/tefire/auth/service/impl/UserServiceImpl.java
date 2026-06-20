/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-06-20 13:57:40
 * @Description: 
 */
package com.tefire.auth.service.impl;

import java.util.Objects;

import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.tefire.auth.constant.RedisKeyConstants;
import com.tefire.auth.domain.dataobject.UserDO;
import com.tefire.auth.domain.mapper.UserDOMapper;
import com.tefire.auth.enums.LoginTypeEnum;
import com.tefire.auth.enums.ResponseCodeEnum;
import com.tefire.auth.model.vo.user.UserLoginReqVO;
import com.tefire.auth.service.UserService;
import com.tefire.framework.common.exception.BizException;
import com.tefire.framework.common.response.Response;
import com.tefire.framework.common.util.JsonUtils;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class UserServiceImpl implements UserService{
    
    @Resource
    private UserDOMapper userDOMapper;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Override
    public Response<String> loginAndRegister(UserLoginReqVO userLoginReqVO) {
        Integer type = userLoginReqVO.getType();
        String phone = userLoginReqVO.getPhone();

        LoginTypeEnum loginTypeEnum = LoginTypeEnum.valueOf(type);

        Long userId = null;

        switch (loginTypeEnum) {
            case VERIFICATION_CODE:
                String verificationCode  = userLoginReqVO.getCode();
                if (StringUtils.isBlank(verificationCode)) {
                    return Response.fail(ResponseCodeEnum.PARAM_NOT_VALID.getErrorCode(), "验证码不能为空");
                }
                String verificationCodeKey = RedisKeyConstants.buildVerificationCodeKey(phone);
                String sentCode = (String) redisTemplate.opsForValue().get(verificationCodeKey);
                
                // 验证码是否正确
                if (!StringUtils.equals(verificationCode, sentCode)) {
                    throw new BizException(ResponseCodeEnum.VERIFICATION_CODE_ERROR);
                }

                // 通过手机号查询记录
                UserDO userDo = userDOMapper.selectByPhone(phone);
                log.info("==> 用户是否注册, phone: {}, userDO: {}", phone, JsonUtils.toJsonString(userDo));

                // 判断是否注册
                if (Objects.isNull(userDo)) {
                    // 若此用户还没有注册，系统自动注册该用户
                    // todo
                } else {
                    // 已注册，则获取其用户 ID
                    userId = userDo.getId();
                }
                break;
            case PASSWORD:
                // todo:
            default:
                break;
        }
        // SaToken 登录用户，并返回 token 令牌
        // todo

        return Response.success("");
    }
}
