package com.tefire.auth.service.impl;

import java.util.Objects;

import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.google.common.base.Preconditions;
import com.tefire.auth.constant.RedisKeyConstants;
import com.tefire.auth.domain.mapper.UserDOMapper;
import com.tefire.auth.enums.LoginTypeEnum;
import com.tefire.auth.enums.ResponseCodeEnum;
import com.tefire.auth.model.vo.user.UpdatePasswordReqVO;
import com.tefire.auth.model.vo.user.UserLoginReqVO;
import com.tefire.auth.rpc.UserRpcService;
import com.tefire.auth.service.AuthService;
import com.tefire.framework.biz.context.holder.LoginUserContextHolder;
import com.tefire.framework.common.exception.BizException;
import com.tefire.framework.common.response.Response;
import com.tefire.user.dto.resp.FindUserByPhoneRspDTO;

import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class AuthServiceImpl implements AuthService{
    
    @Resource
    private UserDOMapper userDOMapper;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Resource
    private TransactionTemplate transactionTemplate;

    @Resource(name = "taskExecutor")
    private ThreadPoolTaskExecutor threadPoolTaskExecutor;
    
    @Resource
    private PasswordEncoder passwordEncoder;

    @Resource
    private UserRpcService userRpcService;

    @Override
    public Response<String> loginAndRegister(UserLoginReqVO userLoginReqVO) {
        Integer type = userLoginReqVO.getType();
        String phone = userLoginReqVO.getPhone();

        LoginTypeEnum loginTypeEnum = LoginTypeEnum.valueOf(type);

        Long userId = null;

        switch (loginTypeEnum) {
            case VERIFICATION_CODE:
                String verificationCode  = userLoginReqVO.getCode();
                // 校验验证码是否为空
                Preconditions.checkArgument(StringUtils.isNotBlank(verificationCode), "验证码不能为空");
                String verificationCodeKey = RedisKeyConstants.buildVerificationCodeKey(phone);
                String sentCode = (String) redisTemplate.opsForValue().get(verificationCodeKey);
                
                // 验证码是否正确
                if (!StringUtils.equals(verificationCode, sentCode)) {
                    throw new BizException(ResponseCodeEnum.VERIFICATION_CODE_ERROR);
                }

                // RPC: 调用用户服务，注册用户
                Long userIdTmp = userRpcService.registerUser(phone);

                // 若调用用户服务，返回的用户 ID 为空，则提示登录失败
                if (Objects.isNull(userIdTmp)) {
                    throw new BizException(ResponseCodeEnum.LOGIN_FAIL);
                }

                userId = userIdTmp;
                break;
            case PASSWORD:
                String password = userLoginReqVO.getPassword();

                // RPC: 调用用户服务，通过手机号查询用户
                FindUserByPhoneRspDTO findUserByPhoneRspDTO = userRpcService.findUserByPhone(phone);
                if (Objects.isNull(findUserByPhoneRspDTO)) {
                    throw new BizException(ResponseCodeEnum.USER_NOT_FOUND);
                }

                String encodePassword = findUserByPhoneRspDTO.getPassword();

                boolean isPasswordCorrect = passwordEncoder.matches(password, encodePassword);

                if (!isPasswordCorrect) {
                    throw new BizException(ResponseCodeEnum.PHONE_OR_PASSWORD_ERROR);
                }

                userId = findUserByPhoneRspDTO.getId();
            default:
                break;
        }
        // SaToken 登录用户，并返回 token 令牌
        StpUtil.login(userId);
        // 获取token
        SaTokenInfo tokenInfo = StpUtil.getTokenInfo();

        return Response.success(tokenInfo.tokenValue);
    }

    @Override
    public Response<?> logout() {
        Long userId = LoginUserContextHolder.getUserId();

        log.info("==> 用户退出登录, userId: {}", userId);

        // 测试ThreadLocal的局限性
        threadPoolTaskExecutor.submit(() -> {
            Long userId2 = LoginUserContextHolder.getUserId();
            log.info("==> 异步线程中获取 userId: {}", userId2);
        });
        // 退出登录 (指定用户 ID)
        StpUtil.logout(userId);
        
        return Response.success();
    }



    @Override
    public Response<?> updatePassword(UpdatePasswordReqVO updatePasswordReqVO) {
        String newPassword = updatePasswordReqVO.getNewPassword();
        // 密码加密
        String encodePassword = passwordEncoder.encode(newPassword);

        // RPC: 调用用户服务：更新密码
        userRpcService.updatePassword(encodePassword);

        return Response.success();
    }

        /**
     * 系统自动注册用户
     * @param phone
     * @return
     */
    // public Long registerUser(String phone) {
    //     return transactionTemplate.execute(status -> {
    //         try {
    //             // 获取全局自增的 ID
    //             Long xiaohashuId = redisTemplate.opsForValue().increment(RedisKeyConstants.BLUENOTE_ID_GENERATOR_KEY);

    //             UserDO userDO = UserDO.builder()
    //                     .phone(phone)
    //                     .xiaohashuId(String.valueOf(xiaohashuId)) // 自动生成小红书号 ID
    //                     .nickname("小红薯" + xiaohashuId) // 自动生成昵称, 如：小红薯10000
    //                     .status(StatusEnum.ENABLE.getValue()) // 状态为启用
    //                     .createTime(LocalDateTime.now())
    //                     .updateTime(LocalDateTime.now())
    //                     .isDeleted(DeletedEnum.NO.getValue()) // 逻辑删除
    //                     .build();

    //             // 添加入库
    //             userDOMapper.insert(userDO);
				
    //             // 获取刚刚添加入库的用户 ID
    //             Long userId = userDO.getId();

    //             // 给该用户分配一个默认角色
    //             UserRoleDO userRoleDO = UserRoleDO.builder()
    //                     .userId(userId)
    //                     .roleId(RoleConstants.COMMON_USER_ROLE_ID)
    //                     .createTime(LocalDateTime.now())
    //                     .updateTime(LocalDateTime.now())
    //                     .isDeleted(DeletedEnum.NO.getValue())
    //                     .build();
    //             userRoleDOMapper.insert(userRoleDO);

    //             RoleDO roleDO = roleDOMapper.selectByPrimaryKey(RoleConstants.COMMON_USER_ROLE_ID);
    //             // 将该用户的角色 ID 存入 Redis 中，指定初始容量为 1，这样可以减少在扩容时的性能开销
    //             List<String> rolse = new ArrayList<>(1);
    //             rolse.add(roleDO.getRoleKey());
    //             String userRolesKey = RedisKeyConstants.buildUserRoleKey(userId);

    //             redisTemplate.opsForValue().set(userRolesKey, JsonUtils.toJsonString(rolse));

    //             return userId;
    //         } catch (Exception e) {
    //             status.setRollbackOnly(); // 标记事务为回滚
    //             log.error("==> 系统注册用户异常: ", e);
    //             return null;
    //         }
    //     });
    // }
}
