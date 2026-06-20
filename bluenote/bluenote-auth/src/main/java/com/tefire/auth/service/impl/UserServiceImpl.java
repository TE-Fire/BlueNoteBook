/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-06-20 13:57:40
 * @Description: 
 */
package com.tefire.auth.service.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.google.common.collect.Lists;
import com.tefire.auth.constant.RedisKeyConstants;
import com.tefire.auth.constant.RoleConstants;
import com.tefire.auth.domain.dataobject.UserDO;
import com.tefire.auth.domain.dataobject.UserRoleDO;
import com.tefire.auth.domain.mapper.UserDOMapper;
import com.tefire.auth.domain.mapper.UserRoleDOMapper;
import com.tefire.auth.enums.LoginTypeEnum;
import com.tefire.auth.enums.ResponseCodeEnum;
import com.tefire.auth.model.vo.user.UserLoginReqVO;
import com.tefire.auth.service.UserService;
import com.tefire.framework.common.enums.DeletedEnum;
import com.tefire.framework.common.enums.StatusEnum;
import com.tefire.framework.common.exception.BizException;
import com.tefire.framework.common.response.Response;
import com.tefire.framework.common.util.JsonUtils;

import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class UserServiceImpl implements UserService{
    
    @Resource
    private UserDOMapper userDOMapper;

    @Resource
    private UserRoleDOMapper userRoleDOMapper;

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
                   userId = registerUser(phone);
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
        StpUtil.login(userId);
        // 获取token
        SaTokenInfo tokenInfo = StpUtil.getTokenInfo();

        return Response.success(tokenInfo.tokenValue);
    }

    /**
     * 系统自动注册用户
     * @param phone
     * @return
     */
    @Transactional(rollbackFor = Exception.class)
    public Long registerUser(String phone) {
        // 获取全局自增的小蓝书 ID
        Long blueNoteId = redisTemplate.opsForValue().increment(RedisKeyConstants.BLUENOTE_ID_GENERATOR_KEY);

        UserDO userDO = UserDO.builder()
                    .phone(phone)
                    .xiaohashuId(String.valueOf(blueNoteId))
                    .nickname("小蓝薯" + blueNoteId)
                    .status(StatusEnum.ENABLE.getValue())
                    .createTime(LocalDateTime.now())
                    .updateTime(LocalDateTime.now())
                    .isDeleted(DeletedEnum.NO.getValue())
                    .build();

        // 添加入库
        userDOMapper.insert(userDO);

        Long userId = userDO.getId();

        // 给该用户分配一个默认角色
        UserRoleDO userRoleDO = UserRoleDO.builder()
                    .userId(userId)
                    .roleId(RoleConstants.COMMON_USER_ROLE_ID)
                    .createTime(LocalDateTime.now())
                    .updateTime(LocalDateTime.now())
                    .isDeleted(DeletedEnum.NO.getValue())
                    .build();
        userRoleDOMapper.insert(userRoleDO);

        // 将普通角色 ID 存入 Redis 中
        List<Long> roles = Lists.newArrayList();
        roles.add(RoleConstants.COMMON_USER_ROLE_ID);
        String userRoleKey = RedisKeyConstants.buildUserRoleKey(phone);
        redisTemplate.opsForValue().set(userRoleKey, JsonUtils.toJsonString(roles));

        return userId;
    }
}
