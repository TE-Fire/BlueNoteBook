package com.tefire.relation.service.impl;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;

import com.tefire.framework.biz.context.holder.LoginUserContextHolder;
import com.tefire.framework.common.exception.BizException;
import com.tefire.framework.common.response.PageResponse;
import com.tefire.framework.common.response.Response;
import com.tefire.framework.common.util.DateUtils;
import com.tefire.framework.common.util.JsonUtils;
import com.tefire.relation.constant.MQConstants;
import com.tefire.relation.constant.RedisKeyConstants;
import com.tefire.relation.domain.dataobject.FollowingDO;
import com.tefire.relation.domain.mapper.FollowingDOMapper;
import com.tefire.relation.enums.LuaResultEnum;
import com.tefire.relation.enums.ResponseCodeEnum;
import com.tefire.relation.model.dto.FollowUserMqDTO;
import com.tefire.relation.model.dto.UnfollowUserMqDTO;
import com.tefire.relation.model.vo.FindFollowingListReqVO;
import com.tefire.relation.model.vo.FindFollowingUserRspVO;
import com.tefire.relation.model.vo.FollowUserReqVO;
import com.tefire.relation.model.vo.UnfollowUserReqVO;
import com.tefire.relation.rpc.UserRpcService;
import com.tefire.relation.service.RelationService;
import com.tefire.user.dto.resp.FindUserByIdRspDTO;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.RandomUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class RelationServiceImpl implements RelationService {
    
    @Resource
    private UserRpcService userRpcService;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Resource
    private RocketMQTemplate rocketMQTemplate;

    @Resource
    private FollowingDOMapper followingDOMapper;

    @Resource(name = "taskExecutor")
    private ThreadPoolTaskExecutor threadPoolTaskExecutor;

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
        FindUserByIdRspDTO findUserByIdRspDTO = userRpcService.findById(followUserId);
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

           // 校验 Lua 脚本执行结果
        if (checkLuaScriptResult(result)) {
            sendFollowMQ(userId, followUserId);
            return Response.success();
        }
 
        // ZSET 不存在
        if (Objects.equals(result, LuaResultEnum.ZSET_NOT_EXIST.getCode())) {
            // 从数据库查询当前用户的关注关系记录
            List<FollowingDO> followingDOS = followingDOMapper.selectByUserId(userId);

            // 随机过期时间
            // 保底1天+随机秒数
            long expireSeconds = 60*60*24 + RandomUtil.randomInt(60*60*24);

            // 若记录为空，直接 ZADD 对象, 并设置过期时间
            if (CollUtil.isEmpty(followingDOS)) {
                DefaultRedisScript<Long> script2 = new DefaultRedisScript<>();
                script2.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/follow_add_and_expire.lua")));
                script2.setResultType(Long.class);

                // TODO: 可以根据用户类型，设置不同的过期时间，若当前用户为大V, 则可以过期时间设置的长些或者不设置过期时间；如不是，则设置的短些
                // 如何判断呢？可以从计数服务获取用户的粉丝数，目前计数服务还没创建，则暂时采用统一的过期策略
                Long result2 = redisTemplate.execute(script2, Collections.singletonList(followingRedisKey), followUserId, timestamp, expireSeconds);
                if (checkLuaScriptResult(result2)) {
                    sendFollowMQ(userId, followUserId);
                    return Response.success();
                }
            } else { // 若记录不为空，则将关注关系数据全量同步到 Redis 中，并设置过期时间；
                // 构建 Lua 参数
                Object[] luaArgs = buildLuaArgs(followingDOS, expireSeconds);

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

        return Response.success();
    }

    @Override
    public Response<?> unfollow(UnfollowUserReqVO unfollowUserReqVO) {
        // 获取取关 ID
        Long unfollowUserId = unfollowUserReqVO.getUnfollowUserId();
        // 当前用 id
        Long userId = LoginUserContextHolder.getUserId();

        // 无法取关自己
        if (Objects.equals(unfollowUserId, userId)) {
            throw new BizException(ResponseCodeEnum.CANT_UNFOLLOW_YOUR_SELF);
        }

        // 校验取关的用户是否存在
        FindUserByIdRspDTO findUserByIdRspDTO = userRpcService.findById(unfollowUserId);
        if (Objects.isNull(findUserByIdRspDTO)) {
            throw new BizException(ResponseCodeEnum.FOLLOW_USER_NOT_EXISTED);
        }

        // 必须是已经关注了的用户才能取关
        String userFollowingKey = RedisKeyConstants.buildUserFollowingKey(userId);
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/unfollow_check_and_delete.lua")));
        script.setResultType(Long.class);

        // 执行 lua 脚本，获取返回结果
        Long result = redisTemplate.execute(script, Collections.singletonList(userFollowingKey), unfollowUserId);
        // 校验 lua 返回结果
        // 取关用户不再关注列表
        if (Objects.equals(result, LuaResultEnum.NOT_FOLLOWED.getCode())) {
            throw new BizException(ResponseCodeEnum.NOT_FOLLOWED);
        }

        if (Objects.equals(result, LuaResultEnum.ZSET_NOT_EXIST.getCode())) {
            List<FollowingDO> followingDOs = followingDOMapper.selectByUserId(userId);

            // 随机过期时间
            // 保底1天+随机秒数
            long expireSeconds = 60*60*24 + RandomUtil.randomInt(60*60*24);

            // 若记录为空，表示未关注任何人
            if (CollUtil.isEmpty(followingDOs)) {
                throw new BizException(ResponseCodeEnum.NOT_FOLLOWED);
            } else { // 若记录不为空，则将关注关系数据全量同步到 Redis 中，并设置过期时间；
                // 构建 Lua 参数
                Object[] luaArgs = buildLuaArgs(followingDOs, expireSeconds);

                // 执行 Lua 脚本，批量同步关注关系数据到 Redis 中
                DefaultRedisScript<Long> script3 = new DefaultRedisScript<>();
                script3.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/follow_batch_add_and_expire.lua")));
                script3.setResultType(Long.class);
                redisTemplate.execute(script3, Collections.singletonList(userFollowingKey), luaArgs);

                // 再次调用上面的 Lua 脚本：unfollow_check_and_delete.lua , 将取关的用户删除
                result = redisTemplate.execute(script, Collections.singletonList(userFollowingKey), unfollowUserId);

                // 再次校验结果
                if (Objects.equals(result, LuaResultEnum.NOT_FOLLOWED.getCode())) {
                    throw new BizException(ResponseCodeEnum.NOT_FOLLOWED);
                }
            }
        }

        // 发送 MQ
        UnfollowUserMqDTO unfollowUserMqDTO = UnfollowUserMqDTO.builder()
                        .userId(userId)
                        .unfollowUserId(unfollowUserId)
                        .createTime(LocalDateTime.now())
                        .build();

        // 构建消息对象，并将 DTO 转成 Json 字符串设置到消息体中
        Message<String> message = MessageBuilder.withPayload(JsonUtils.toJsonString(unfollowUserMqDTO)).build();

        // 通过冒号连接, 可让 MQ 发送给主题 Topic 时，携带上标签 Tag
        String destination = MQConstants.TOPIC_FOLLOW_OR_UNFOLLOW + ":" + MQConstants.TAG_UNFOLLOW;

        log.info("==> 开始发送取关操作 MQ, 消息体: {}", unfollowUserMqDTO);

        // hashKey 来确定应该将消息发送到哪个队列，以确保同一个 hashKey 的消息会按照顺序进入同一个队列，从而实现顺序消费
        String hashKey = String.valueOf(userId); 
        // 异步发送 MQ 消息，提升接口响应速度
        rocketMQTemplate.asyncSendOrderly(destination, message, hashKey, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.info("==> MQ 发送成功，SendResult: {}", sendResult);
            }

            @Override
            public void onException(Throwable throwable) {
                log.error("==> MQ 发送异常: ", throwable);
            }
        });

        return Response.success();
    }
    
    @SuppressWarnings("null")
    @Override
    public PageResponse<FindFollowingUserRspVO> findFollowingList(FindFollowingListReqVO findFollowingListReqVO) {
        Long userId = findFollowingListReqVO.getUserId();
        Integer pageNo = findFollowingListReqVO.getPageNo();

        // 先从 redis 中查询
        String userFollowingKey = RedisKeyConstants.buildUserFollowingKey(userId);

        // 查询目标用户关注列表 zset 的总大小
        Long total = redisTemplate.opsForZSet().zCard(userFollowingKey);

        // 返参
        List<FindFollowingUserRspVO> findFollowingUserRspVOS = null;

        // 每页展示 10 条数据
        long limit = 10;

        if (total > 0) { // 缓存中有数据
           
            // 计算一共多少页
            long totalPage = PageResponse.getTotalPage(total, limit);

            // 请求页数超过总页数
            if (pageNo > totalPage) return PageResponse.success(null, pageNo, total);

            // 准备从 Redis 中查询 ZSet 分页数据
            // 每页 10 个元素，计算偏移量
            long offset = PageResponse.getOffset(pageNo, limit);

            // 使用 ZREVRANGEBYSCORE 命令按 score 降序获取元素，同时使用 LIMIT 子句实现分页
            // 注意：这里使用了 Double.POSITIVE_INFINITY 和 Double.NEGATIVE_INFINITY 作为分数范围
            // 因为关注列表最多有 1000 个元素，这样可以确保获取到所有的元素
            Set<Object> followingUserIdsSet = redisTemplate.opsForZSet()
                .reverseRangeByScore(userFollowingKey, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, offset, limit);

            if (CollUtil.isNotEmpty(followingUserIdsSet)) {
                List<Long> userIds = followingUserIdsSet.stream().map(object -> Long.valueOf(object.toString())).toList();
                // RPC: 批量查询用户信息
                findFollowingUserRspVOS = rpcUserServiceAndDTO2VO(userIds, findFollowingUserRspVOS);
            }
        } else {
            // 若 Redis 中没有数据，则从数据库查询
            // 查询记录总量
            long count = followingDOMapper.selectCountByUserId(userId);

            long totalPage = PageResponse.getTotalPage(count, limit);

            if (pageNo > totalPage) return PageResponse.success(null, pageNo, total);
            
            // 偏移量
            long offset = PageResponse.getOffset(pageNo, limit);

            // 分页查询
            List<FollowingDO> followingDOs = followingDOMapper.selectPageListByUserId(userId, offset, limit);
            
            // 赋值真实的记录总数
			total = count;

            // 若记录不为空
            if (CollUtil.isNotEmpty(followingDOs)) {
                // 提取所有关注用户 ID 到集合中
                List<Long> userIds = followingDOs.stream().map(FollowingDO::getFollowingUserId).toList();

                // RPC: 调用用户服务，并将 DTO 转换为 VO
                findFollowingUserRspVOS = rpcUserServiceAndDTO2VO(userIds, findFollowingUserRspVOS);

                // 异步将关注列表全量同步到 Redis
                threadPoolTaskExecutor.submit(() -> syncFollowingList2Redis(userId));
            }
        }

        return PageResponse.success(findFollowingUserRspVOS, pageNo, total);
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
    private static boolean checkLuaScriptResult(Long result) {
        LuaResultEnum luaResultEnum = LuaResultEnum.valueOf(result);
        if (Objects.isNull(luaResultEnum)) throw new RuntimeException("Lua 返回结果错误");
        // 校验 Lua 脚本执行结果
        switch (luaResultEnum) {
            // 关注数已达到上限
            case FOLLOW_LIMIT -> throw new BizException(ResponseCodeEnum.FOLLOWING_COUNT_LIMIT);
            // 已经关注了该用户
            case ALREADY_FOLLOWED -> throw new BizException(ResponseCodeEnum.ALREADY_FOLLOWED);
            // 关注成功
            case FOLLOW_SUCCESS -> {
                return true;
            }
            default -> {
                log.debug("ZKEY 不存在");
                return false;
            }
        }
    }

    private void sendFollowMQ(Long userId, Long followUserId) {
        FollowUserMqDTO followUserMqDTO = FollowUserMqDTO.builder()
                    .userId(userId)
                    .followUserId(followUserId)
                    .createTime(LocalDateTime.now())
                    .build();
        
        Message<String> message = MessageBuilder.withPayload(JsonUtils.toJsonString(followUserMqDTO)).build();
        String destination = MQConstants.TOPIC_FOLLOW_OR_UNFOLLOW + ":" + MQConstants.TAG_FOLLOW;
        
        log.info("==> 开始发送关注操作 MQ, 消息体: {}", followUserMqDTO);
        
        String hashKey = String.valueOf(userId);
        rocketMQTemplate.asyncSendOrderly(destination, message, hashKey, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.info("==> MQ 发送成功，SendResult: {}", sendResult);
            }
            
            @Override
            public void onException(Throwable throwable) {
                log.error("==> MQ 发送异常: ", throwable);
            }
        });
    }

     /**
     * 全量同步关注列表至 Redis 中
     */
    private void syncFollowingList2Redis(Long userId) {
        List<FollowingDO> followingDOs = followingDOMapper.selectAllByUserId(userId);
        if (CollUtil.isNotEmpty(followingDOs)) {
            // 用户关注列表 Redis Key
            String followingListRedisKey = RedisKeyConstants.buildUserFollowingKey(userId);
            // 随机过期时间
            // 保底1天+随机秒数
            long expireSeconds = 60*60*24 + RandomUtil.randomInt(60*60*24);
            // 构建 Lua 参数
            Object[] luaArgs = buildLuaArgs(followingDOs, expireSeconds);

            // 执行 Lua 脚本，批量同步关注关系数据到 Redis 中
            DefaultRedisScript<Long> script = new DefaultRedisScript<>();
            script.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/follow_batch_add_and_expire.lua")));
            script.setResultType(Long.class);
            redisTemplate.execute(script, Collections.singletonList(followingListRedisKey), luaArgs);
        }
    }

    /**
     * RPC: 调用用户服务，并将 DTO 转换为 VO
     * @param userIds
     * @param findFollowingUserRspVOS
     * @return
     */
    private List<FindFollowingUserRspVO> rpcUserServiceAndDTO2VO(List<Long> userIds, List<FindFollowingUserRspVO> findFollowingUserRspVOS) {
        // RPC: 批量查询用户信息
        List<FindUserByIdRspDTO> findUserByIdRspDTOS = userRpcService.findByIds(userIds);

        // 若不为空，DTO 转 VO
        if (CollUtil.isNotEmpty(findUserByIdRspDTOS)) {
            findFollowingUserRspVOS = findUserByIdRspDTOS.stream()
                    .map(dto -> FindFollowingUserRspVO.builder()
                            .userId(dto.getId())
                            .avatar(dto.getAvatar())
                            .nickname(dto.getNickName())
                            .introduction(dto.getIntroduction())
                            .build())
                    .toList();
        }
        return findFollowingUserRspVOS;
    }
}
