package com.tefire.user.biz.service.impl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.tefire.framework.biz.context.holder.LoginUserContextHolder;
import com.tefire.framework.common.enums.DeletedEnum;
import com.tefire.framework.common.enums.StatusEnum;
import com.tefire.framework.common.exception.BizException;
import com.tefire.framework.common.response.Response;
import com.tefire.framework.common.util.JsonUtils;
import com.tefire.framework.common.util.ParamUtils;
import com.tefire.user.biz.constant.RedisKeyConstants;
import com.tefire.user.biz.constant.RoleConstants;
import com.tefire.user.biz.domain.dataobject.RoleDO;
import com.tefire.user.biz.domain.dataobject.UserDO;
import com.tefire.user.biz.domain.dataobject.UserRoleDO;
import com.tefire.user.biz.domain.mapper.RoleDOMapper;
import com.tefire.user.biz.domain.mapper.UserDOMapper;
import com.tefire.user.biz.domain.mapper.UserRoleDOMapper;
import com.tefire.user.biz.enums.ResponseCodeEnum;
import com.tefire.user.biz.enums.SexEnum;
import com.tefire.user.biz.model.vo.UpdateUserInfoReqVO;
import com.tefire.user.biz.rpc.DistributedIdGeneratorRpcService;
import com.tefire.user.biz.rpc.OssRpcService;
import com.tefire.user.biz.service.UserService;
import com.tefire.user.dto.req.FindUserByIdReqDTO;
import com.tefire.user.dto.req.FindUserByPhoneReqDTO;
import com.tefire.user.dto.req.FindUsersByIdsReqDTO;
import com.tefire.user.dto.req.RegisterUserReqDTO;
import com.tefire.user.dto.req.UpdateUserPasswordReqDTO;
import com.tefire.user.dto.resp.FindUserByIdRspDTO;
import com.tefire.user.dto.resp.FindUserByPhoneRspDTO;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.RandomUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class UserServiceImpl implements UserService {
    
    @Resource
    private UserDOMapper userDOMapper;

    @Resource
    private RoleDOMapper roleDOMapper;

    @Resource
    private UserRoleDOMapper userRoleDOMapper;

    @Resource
    private OssRpcService ossRpcService;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Resource
    private DistributedIdGeneratorRpcService distributedIdGeneratorRpcService;

    @Resource(name = "taskExecutor")
    private ThreadPoolTaskExecutor threadPoolTaskExecutor;

    /**
     * 用户信息本地缓存
     */
    private static final Cache<Long, FindUserByIdRspDTO> LOCAL_CACHE = Caffeine.newBuilder()
            .initialCapacity(10000) // 设置初始容量为 10000 个条目
            .maximumSize(10000) // 设置缓存的最大容量为 10000 个条目
            .expireAfterWrite(1, TimeUnit.HOURS) // 设置缓存条目在写入后 1 小时过期
            .build();

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
            String avatarFilePath = ossRpcService.uploadFile(avatar);
            log.info("==> 调用 oss 服务成功，上传头像，url：{}", avatarFilePath);

            if (StringUtils.isBlank(avatarFilePath)) {
                throw new BizException(ResponseCodeEnum.UPLOAD_AVATAR_FAIL);
            }
            userDO.setAvatar(avatarFilePath);
            needUpdate = true;
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
             String backgroundImg = ossRpcService.uploadFile(backgroundImgFile);
            log.info("==> 调用 oss 服务成功，上传背景图，url：{}", backgroundImg);

			// 若上传背景图失败，则抛出业务异常
            if (StringUtils.isBlank(backgroundImg)) {
                throw new BizException(ResponseCodeEnum.UPLOAD_BACKGROUND_IMG_FAIL);
            }

            userDO.setBackgroundImg(backgroundImg);
            needUpdate = true;
        }

        if (needUpdate) {
            // 更新用户信息
            userDO.setUpdateTime(LocalDateTime.now());
            userDOMapper.updateByPrimaryKeySelective(userDO);
        }

        return Response.success();
    }

   @Override
    @Transactional(rollbackFor = Exception.class)
    public Response<Long> register(RegisterUserReqDTO registerUserReqDTO) {
        String phone = registerUserReqDTO.getPhone();

        // 先判断该手机号是否已被注册
        UserDO userDO1 = userDOMapper.selectByPhone(phone);

        log.info("==> 用户是否注册, phone: {}, userDO: {}", phone, JsonUtils.toJsonString(userDO1));

        // 若已注册，则直接返回用户 ID
        if (Objects.nonNull(userDO1)) {
            return Response.success(userDO1.getId());
        }

        // 否则注册新用户
        // 获取全局自增的小哈书 ID
        // Long xiaohashuId = redisTemplate.opsForValue().increment(RedisKeyConstants.BLUENOTE_ID_GENERATOR_KEY);

        // RPC: 调用分布式 ID 生成服务生成小哈书 ID
        String bluenoteId = distributedIdGeneratorRpcService.getBluenoteId();

         // RPC: 调用分布式 ID 生成服务生成用户 ID
        Long userId = Long.parseLong(distributedIdGeneratorRpcService.getUserId());


        UserDO userDO = UserDO.builder()
                .id(userId)
                .phone(phone)
                .xiaohashuId(String.valueOf(bluenoteId)) // 自动生成小红书号 ID
                .nickname("小红薯" + bluenoteId) // 自动生成昵称, 如：小红薯10000
                .status(StatusEnum.ENABLE.getValue()) // 状态为启用
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .isDeleted(DeletedEnum.NO.getValue()) // 逻辑删除
                .build();

        // 添加入库
        userDOMapper.insert(userDO);

        // 获取刚刚添加入库的用户 ID
        // Long userId = userDO.getId();

        // 给该用户分配一个默认角色
        UserRoleDO userRoleDO = UserRoleDO.builder()
                .userId(userId)
                .roleId(RoleConstants.COMMON_USER_ROLE_ID)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .isDeleted(DeletedEnum.NO.getValue())
                .build();
        userRoleDOMapper.insert(userRoleDO);

        RoleDO roleDO = roleDOMapper.selectByPrimaryKey(RoleConstants.COMMON_USER_ROLE_ID);

        // 将该用户的角色 ID 存入 Redis 中
        List<String> roles = new ArrayList<>(1);
        roles.add(roleDO.getRoleKey());

        String userRolesKey = RedisKeyConstants.buildUserRoleKey(userId);
        redisTemplate.opsForValue().set(userRolesKey, JsonUtils.toJsonString(roles));

        return Response.success(userId);
    }

    @Override
    public Response<FindUserByPhoneRspDTO> findByPhone(FindUserByPhoneReqDTO findUserByPhoneReqDTO) {
        String phone = findUserByPhoneReqDTO.getPhone();

        UserDO userDO = userDOMapper.selectByPhone(phone);

        if (Objects.isNull(userDO)) {
            throw new BizException(ResponseCodeEnum.USER_NOT_FOUND);
        }

        FindUserByPhoneRspDTO findUserByPhoneRspDTO = FindUserByPhoneRspDTO.builder()
                .id(userDO.getId())
                .password(userDO.getPassword())
                .build();

        return Response.success(findUserByPhoneRspDTO);
    }

    @Override
    public Response<?> updatePassword(UpdateUserPasswordReqDTO updateUserPasswordReqDTO) {
        // 获取当前请求对应的用户 ID
        Long userId = LoginUserContextHolder.getUserId();

        UserDO userDO = UserDO.builder()
                .id(userId)
                .password(updateUserPasswordReqDTO.getEncodePassword()) // 加密后的密码
                .updateTime(LocalDateTime.now())
                .build();
        // 更新密码
        userDOMapper.updateByPrimaryKeySelective(userDO);

        return Response.success();
    }

    @Override
    public Response<FindUserByIdRspDTO> findById(FindUserByIdReqDTO findUserByIdReqDTO) {
        Long userId = findUserByIdReqDTO.getId();

        // 先查询本地缓存
        FindUserByIdRspDTO findUserByIdRspDTOLocalCache  = LOCAL_CACHE.getIfPresent(userId);
        if (Objects.nonNull(findUserByIdRspDTOLocalCache)) {
            log.info("==> 命中了本地缓存；{}", findUserByIdRspDTOLocalCache);
            return Response.success(findUserByIdRspDTOLocalCache);
        }

        String userInfoKey = RedisKeyConstants.buildUserInfoKey(userId);

        String userInfoRedisValue  = (String) redisTemplate.opsForValue().get(userInfoKey);

        if (StringUtils.isNotBlank(userInfoRedisValue)) {
             // 将存储的 Json 字符串转换成对象，并返回
            FindUserByIdRspDTO findUserByIdRspDTO = JsonUtils.parseObject(userInfoRedisValue, FindUserByIdRspDTO.class);
            // 异步线程中将用户信息存入本地缓存
            threadPoolTaskExecutor.submit(() -> {
                if (Objects.nonNull(findUserByIdRspDTO)) {
                    // 写入本地缓存
                    LOCAL_CACHE.put(userId, findUserByIdRspDTO);
                }
            });
            return Response.success(findUserByIdRspDTO);
        }
        UserDO userDO = userDOMapper.selectByPrimaryKey(userId);

        if (Objects.isNull(userDO)) {
            threadPoolTaskExecutor.execute(() -> {
                 // 防止缓存穿透，将空数据存入 Redis 缓存 (过期时间不宜设置过长)
                // 保底1分钟 + 随机秒数
                long expireSeconds = 60 + RandomUtil.randomInt(60);
                redisTemplate.opsForValue().set(userInfoKey, "null", expireSeconds, TimeUnit.SECONDS);
            });
            throw new BizException(ResponseCodeEnum.USER_NOT_FOUND);
        }

        FindUserByIdRspDTO findUserByIdRspDTO = FindUserByIdRspDTO.builder()
                     .id(userDO.getId())
                     .nickName(userDO.getNickname())
                     .avatar(userDO.getAvatar())
                     .introduction(userDO.getIntroduction())
                     .build();

        // 异步将信息存入 Redis 缓存，提升响应速度
        threadPoolTaskExecutor.submit(() -> {
            // 过期时间（保底1天 + 随机秒数，将缓存过期时间打散，防止同一时间大量缓存失效，导致数据库压力太大）
            long expireSeconds = 60*60*24 + RandomUtil.randomInt(60*60*24);
            redisTemplate.opsForValue().set(userInfoKey, JsonUtils.toJsonString(findUserByIdRspDTO), expireSeconds, TimeUnit.SECONDS);
        });

        return Response.success(findUserByIdRspDTO);
    }

    @SuppressWarnings("null")
    @Override
    public Response<List<FindUserByIdRspDTO>> findByIds(FindUsersByIdsReqDTO findUsersByIdsReqDTO) {
        List<Long> userIds = findUsersByIdsReqDTO.getIds();

        // 构建 Redis Key 集合
        List<String> redisKeys = userIds.stream()
                    .map(RedisKeyConstants::buildUserInfoKey)
                    .toList();

        // 先从 redis 查询， multiGet 批量查询提升性能
        List<Object> redisValues = redisTemplate.opsForValue().multiGet(redisKeys);
        // 如果缓存不为空
        if (CollUtil.isNotEmpty(redisValues)) {
            // 过滤为空的数据
            redisValues = redisValues.stream().filter(Objects::nonNull).toList();
        }

        // 返参
        List<FindUserByIdRspDTO> findUserByIdRspDTOs = Lists.newArrayList();

        // 将过滤后的缓存集合转换为 DTO 返参实体类
        if (CollUtil.isNotEmpty(redisValues)) {
            findUserByIdRspDTOs = redisValues.stream()
                            .map(value -> JsonUtils.parseObject(String.valueOf(value), FindUserByIdRspDTO.class))
                            .collect(Collectors.toList());
        }

        // 如果被查询的用户信息，都在 Redis 缓存中, 则直接返回
        if (CollUtil.size(findUserByIdRspDTOs) == CollUtil.size(userIds)) {
            return Response.success(findUserByIdRspDTOs);
        }

        // 还有另外两种情况：一种是缓存里没有用户信息数据，还有一种是缓存里数据不全，需要从数据库中补充
        // 筛选出缓存里没有的用户数据，去查数据库
        List<Long> userIdsNeedQuery = null;

        if (CollUtil.isNotEmpty(findUserByIdRspDTOs)) {
            // 将 findUserByIdRspDTOs 转换为 map
            Map<Long, FindUserByIdRspDTO> map = findUserByIdRspDTOs.stream()
                    .collect(Collectors.toMap(FindUserByIdRspDTO::getId, p -> p));

            // 筛选出需要查 DB 的用户 ID
            userIdsNeedQuery = userIds.stream()
                    .filter(id -> Objects.isNull(map.get(id)))
                    .toList();
        } else {  // 缓存中一条用户信息都没查到，则提交的用户 ID 集合都需要查数据库
            userIdsNeedQuery = userIds;
        }

        // 从数据库查询数据
        List<UserDO> userDOs = userDOMapper.selectByIds(userIdsNeedQuery);

        List<FindUserByIdRspDTO> findUserByIdRspDTOS2 = null;

        // 若数据库查询记录不为空
        if (CollUtil.isNotEmpty(userDOs)) {
            // DO -> DTO
            findUserByIdRspDTOS2 = userDOs.stream()
                    .map(userDO -> FindUserByIdRspDTO.builder()
                            .id(userDO.getId())
                            .nickName(userDO.getNickname())
                            .avatar(userDO.getAvatar())
                            .introduction(userDO.getIntroduction())
                            .build())
                    .collect(Collectors.toList());

            // TODO: 异步线程将用户信息同步到 Redis 中
            List<FindUserByIdRspDTO> finalFindUserByIdRspDTOS = findUserByIdRspDTOS2;
            threadPoolTaskExecutor.submit(() -> {
                // DTO 转 Map
                Map<Long, FindUserByIdRspDTO> map = finalFindUserByIdRspDTOS.stream()
                    .collect(Collectors.toMap(FindUserByIdRspDTO::getId, p -> p));

                // 执行 pipeline 操作
                redisTemplate.executePipelined(new SessionCallback<>() {
                    @Override
                    public Object execute(RedisOperations operations) {
                        for (UserDO userDO : userDOs) {
                            Long userId = userDO.getId();

                            // 用户信息缓存 Redis Key
                            String userInfoRedisKey = RedisKeyConstants.buildUserInfoKey(userId);

                            // DTO 转 JSON
                            FindUserByIdRspDTO findUserByIdRspDTO = map.get(userId);
                            String value = JsonUtils.toJsonString(findUserByIdRspDTO);

                            // 过期时间（保底1天 + 随机秒数，将缓存过期时间打散，防止同一时间大量缓存失效，导致数据库压力太大）
                            long expireSeconds = 60*60*24 + RandomUtil.randomInt(60*60*24);

                            operations.opsForValue().set(userInfoRedisKey, value, expireSeconds, TimeUnit.SECONDS);
                        }
                        return null;
                    }
                });
            });
        }

        // 合并数据
        if (CollUtil.isNotEmpty(findUserByIdRspDTOS2)) {
            findUserByIdRspDTOs.addAll(findUserByIdRspDTOS2);
        }

        return Response.success(findUserByIdRspDTOs);
    }
}