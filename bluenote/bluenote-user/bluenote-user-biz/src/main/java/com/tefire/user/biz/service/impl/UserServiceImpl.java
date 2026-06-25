package com.tefire.user.biz.service.impl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.google.common.base.Preconditions;
import com.tefire.framework.biz.context.holder.LoginUserContextHolder;
import com.tefire.framework.common.response.Response;
import com.tefire.framework.common.util.ParamUtils;
import com.tefire.user.biz.domain.dataobject.UserDO;
import com.tefire.user.biz.domain.mapper.UserDOMapper;
import com.tefire.user.biz.enums.ResponseCodeEnum;
import com.tefire.user.biz.enums.SexEnum;
import com.tefire.user.biz.model.vo.UpdateUserInfoReqVO;
import com.tefire.user.biz.service.UserService;

import jakarta.annotation.Resource;

@Service
public class UserServiceImpl implements UserService {
    
    @Resource
    private UserDOMapper userDOMapper;

    @SuppressWarnings("null")
    @Override
    public Response<?> updateUserInfo(UpdateUserInfoReqVO updateUserInfoReqVO) {
        UserDO userDO = new UserDO();

        // 设置当前修改用户的 ID
        userDO.setId(LoginUserContextHolder.getUserId());

        // 标识位：是否需要更新
        boolean needUpdate = false;
        
        // 头像
        MultipartFile avatar = updateUserInfoReqVO.getAvatar();

        if (Objects.nonNull(avatar)) {
            // todo: 调用对象存储服务上传文件
        }

        // 昵称
        String nickname = updateUserInfoReqVO.getNickname();
        if (StringUtils.isNotBlank(nickname)) {
            Preconditions.checkArgument(ParamUtils.checkNickname(nickname), ResponseCodeEnum.NICK_NAME_VALID_FAIL.getErrorMessage());
            userDO.setNickname(nickname);
            needUpdate = true;
        }

        // 小蓝书号
        String bluenoteId = updateUserInfoReqVO.getXiaohashuId();
        if (StringUtils.isNotBlank(bluenoteId)) {
            Preconditions.checkArgument(ParamUtils.checkBluenoteId(bluenoteId), ResponseCodeEnum.XIAOHASHU_ID_VALID_FAIL.getErrorMessage());
            userDO.setXiaohashuId(bluenoteId);
            needUpdate = true;
        }

        // 性别
        Integer sex = updateUserInfoReqVO.getSex();
        if (Objects.nonNull(sex)) {
            Preconditions.checkArgument(SexEnum.isValid(sex), ResponseCodeEnum.SEX_VALID_FAIL.getErrorMessage());
            userDO.setSex(sex);
            needUpdate = true;
        }

        // 生日
        LocalDate birthday = updateUserInfoReqVO.getBirthday();
        if (Objects.nonNull(birthday)) {
            userDO.setBirthday(birthday);
            needUpdate = true;
        }

        // 个人简介
        String introduction = updateUserInfoReqVO.getIntroduction();
        if (StringUtils.isNotBlank(introduction)) {
            Preconditions.checkArgument(ParamUtils.checkLength(introduction, 100), ResponseCodeEnum.INTRODUCTION_VALID_FAIL.getErrorMessage());
            userDO.setIntroduction(introduction);
            needUpdate = true;
        }

        // 背景图
        MultipartFile backgroundImgFile = updateUserInfoReqVO.getBackgroundImg();
        if (Objects.nonNull(backgroundImgFile)) {
            // todo: 调用对象存储服务上传文件
        }

        if (needUpdate) {
            // 更新用户信息
            userDO.setUpdateTime(LocalDateTime.now());
            userDOMapper.updateByPrimaryKeySelective(userDO);
        }

        return Response.success();
    }
}
