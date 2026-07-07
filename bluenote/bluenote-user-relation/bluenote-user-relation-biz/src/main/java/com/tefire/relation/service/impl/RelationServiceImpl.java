package com.tefire.relation.service.impl;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

import com.tefire.framework.biz.context.holder.LoginUserContextHolder;
import com.tefire.framework.common.exception.BizException;
import com.tefire.framework.common.response.Response;
import com.tefire.framework.common.util.DateUtils;
import com.tefire.relation.constant.RedisKeyConstants;
import com.tefire.relation.domain.dataobject.FollowingDO;
import com.tefire.relation.domain.mapper.FollowingDOMapper;
import com.tefire.relation.enums.LuaResultEnum;
import com.tefire.relation.enums.ResponseCodeEnum;
import com.tefire.relation.model.vo.FollowUserReqVO;
import com.tefire.relation.rpc.UserRpcService;
import com.tefire.relation.service.RelationService;
import com.tefire.user.dto.resp.FindUserByIdRspDTO;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.RandomUtil;
import jakarta.annotation.Resource;

public class RelationServiceImpl implements RelationService {
    
    @Resource
    private UserRpcService userRpcService;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Resource
    private FollowingDOMapper followingDOMapper;

    @Override
    public Response<?> follow(FollowUserReqVO followUserReqVO) {
        
        Long followUserId = followUserReqVO.getFollowUserId();

        // 获取当前用户 id 
        Long userId = LoginUserContextHolder.getUserId();

        // 用户不能关注自己
        if (Objects.equals(userId, followUserId)) {
            throw new BizException(ResponseCodeEnum.CANT_FOLLOW_YOUR_SELF);
        }

        // 关注的用户是否存在
        FindUserByIdRspDTO findUserByIdRspDTO = userRpcService.findById(userId);
        if (Objects.isNull(findUserByIdRspDTO)) {
            throw new BizException(ResponseCodeEnum.FOLLOW_USER_NOT_EXISTED);
        }

        // 构建当前用户关注列表的 RedisKey
        String followingRedisKey = RedisKeyConstants.buildUserFollowingKey(userId);
        // Lua 脚本路径
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/follow_check_and_add.lua")));
        // 返回值类型
        script.setResultType(Long.class);
        // 当前时间
        long timestamp = DateUtils.localDateTime2Timestamp(LocalDateTime.now());

        // 执行 lua 脚本，获取执行结果
        Long result = redisTemplate.execute(script, Collections.singletonList(followingRedisKey), followUserId, timestamp);

        LuaResultEnum luaResultEnum = LuaResultEnum.valueOf(result);

        if (Objects.isNull(luaResultEnum)) throw new RuntimeException("Lua 返回结果错误");

        switch (luaResultEnum) {
            // 关注人数达到上限
            case FOLLOW_LIMIT -> throw new BizException(ResponseCodeEnum.FOLLOWING_COUNT_LIMIT);
            // 已经关注该用户
            case ALREADY_FOLLOWED -> throw new BizException(ResponseCodeEnum.ALREADY_FOLLOWED);
        
            case ZSET_NOT_EXIST -> {
                // 从数据库中查询当前用户的关注记录
                List<FollowingDO> followingDOs = followingDOMapper.selectByUserId(userId);

                 // 随机过期时间
                // 保底1天+随机秒数
                long expireSeconds = 60*60*24 + RandomUtil.randomInt(60*60*24);

                // 若记录为空， ZADD 关系数据库， 设置过期时间
                if (CollUtil.isEmpty(followingDOs)) {
                    DefaultRedisScript<Long> script2 = new DefaultRedisScript<>();
                    script2.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/follow_add_and_expire.lua")));
                    script2.setResultType(Long.class);

                    // TODO: 可以根据用户类型，设置不同的过期时间，若当前用户为大V, 则可以过期时间设置的长些或者不设置过期时间；如不是，则设置的短些
                    // 如何判断呢？可以从计数服务获取用户的粉丝数，目前计数服务还没创建，则暂时采用统一的过期策略
                    redisTemplate.execute(script2, Collections.singletonList(followingRedisKey), followUserId, timestamp, expireSeconds);
                } else { // 若记录不为空，将关注关系全量同步到redis
                    // 构建 Lua 参数
                    Object[] luaArgs = buildLuaArgs(followingDOs, expireSeconds);

                     // 执行 Lua 脚本，批量同步关注关系数据到 Redis 中
                    DefaultRedisScript<Long> script3 = new DefaultRedisScript<>();
                    script3.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/follow_batch_add_and_expire.lua")));
                    script3.setResultType(Long.class);
                    redisTemplate.execute(script3, Collections.singletonList(followingRedisKey), luaArgs);

                    // 再次调用上面的 Lua 脚本：follow_check_and_add.lua , 将最新的关注关系添加进去
                    result = redisTemplate.execute(script, Collections.singletonList(followingRedisKey), followUserId, timestamp);
                    checkLuaScriptResult(result);
                }
            }
        }
         
        // TODO: 发送 MQ

        return Response.success();
    }

    /**
     * 构建 Lua 脚本参数
     *
     * @param followingDOS
     * @param expireSeconds
     * @return
     */
    private static Object[] buildLuaArgs(List<FollowingDO> followingDOs, long expireSeconds) {
        int argsLength = followingDOs.size() * 2 + 1; // 每个关注关系有 2 个参数（score 和 value），再加一个过期时间
        Object[] luaArgs = new Object[argsLength];

        int i = 0;
        for (FollowingDO following : followingDOs) {
            luaArgs[i] = DateUtils.localDateTime2Timestamp(following.getCreateTime()); // 关注时间作为 score
            luaArgs[i + 1] = following.getFollowingUserId();          // 关注的用户 ID 作为 ZSet value
            i += 2;
        }

        luaArgs[argsLength - 1] = expireSeconds; // 最后一个参数是 ZSet 的过期时间

        return luaArgs;
    }

      /**
     * 校验 Lua 脚本结果，根据状态码抛出对应的业务异常
     * @param result
     */
    private static void checkLuaScriptResult(Long result) {
        LuaResultEnum luaResultEnum = LuaResultEnum.valueOf(result);
        if (Objects.isNull(luaResultEnum)) throw new RuntimeException("Lua 返回结果错误");
        // 校验 Lua 脚本执行结果
        switch (luaResultEnum) {
            // 关注数已达到上限
            case FOLLOW_LIMIT -> throw new BizException(ResponseCodeEnum.FOLLOWING_COUNT_LIMIT);
            // 已经关注了该用户
            case ALREADY_FOLLOWED -> throw new BizException(ResponseCodeEnum.ALREADY_FOLLOWED);
        }
    }
}
