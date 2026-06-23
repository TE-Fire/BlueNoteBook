package com.tefire.gateway.auth;

import cn.dev33.satoken.stp.StpInterface;
import cn.hutool.core.collection.CollUtil;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.tefire.gateway.constant.RedisKeyConstants;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-06-22 22:04:25
 * @Description: Sa-Token 自定义权限验证接口实现
 * 
 * <p>
 * 该类实现了 Sa-Token 框架的 {@link StpInterface} 接口，是 Sa-Token 框架与业务系统之间的
 * "桥梁"。Sa-Token 框架本身不存储任何业务数据（用户、角色、权限），它只是一个规则引擎。
 * 当调用 {@link cn.dev33.satoken.stp.StpUtil#checkPermission(String)} 或
 * {@link cn.dev33.satoken.stp.StpUtil#checkRole(String)} 时，框架会调用本类的方法
 * 获取用户的角色/权限列表，然后进行比对判断。
 * </p>
 * 
 * <p>
 * <strong>数据来源：</strong>
 * <ul>
 *   <li>Redis 缓存：用户-角色关系存储在 Key: {@code user:roles:{userId}}</li>
 *   <li>Redis 缓存：角色-权限关系存储在 Key: {@code role:permissions:{roleKey}}</li>
 * </ul>
 * 数据由 auth 服务在用户登录时写入 Redis，并由 {@code PushRolePermissions2RedisRunner}
 * 在服务启动时同步角色权限数据。
 * </p>
 * 
 * <p>
 * <strong>Sa-Token 鉴权流程：</strong>
 * <pre>
 * StpUtil.checkPermission("app:note:publish")
 *         │
 *         ▼
 *   Sa-Token 框架调用 getPermissionList(loginId)
 *         │
 *         ▼
 *   本类从 Redis 获取用户角色 → 获取角色权限 → 返回权限列表
 *         │
 *         ▼
 *   框架比对：权限是否在列表中？
 *         │
 *    ┌────┴────┐
 *    ↓         ↓
 *   是         否
 *    ↓         ↓
 *  放行    抛出 NotPermissionException
 * </pre>
 * </p>
 */
@Component
@Slf4j
public class StpInterfaceImpl implements StpInterface {

    /**
     * Redis 操作模板，用于从 Redis 获取用户角色和权限数据
     */
    @Resource
    private RedisTemplate<String, String> redisTemplate;

    /**
     * JSON 序列化工具，用于将 Redis 中的 JSON 字符串解析为对象
     */
    @Resource
    private ObjectMapper objectMapper;


    /**
     * 获取用户的权限列表
     * 
     * <p>
     * 该方法被 Sa-Token 框架在调用 {@link cn.dev33.satoken.stp.StpUtil#checkPermission(String)}
     * 时自动调用，返回当前用户拥有的所有权限 Key 列表。
     * </p>
     * 
     * <p>
     * <strong>数据获取流程：</strong>
     * <ol>
     *   <li>构建 Redis Key: {@code user:roles:{userId}}</li>
     *   <li>获取用户的角色 Key 列表（如 ["common_user"]）</li>
     *   <li>批量构建角色权限 Redis Key: {@code role:permissions:{roleKey}}</li>
     *   <li>批量查询所有角色对应的权限列表</li>
     *   <li>合并所有权限并返回</li>
     * </ol>
     * </p>
     * 
     * @param loginId 用户登录 ID（即用户 ID）
     * @param loginType 登录类型（默认 "login"）
     * @return 用户拥有的权限 Key 列表，如果用户无角色或查询失败返回 null 或空列表
     */
    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        log.info("## 获取用户权限列表, loginId: {}", loginId);

        // 构建用户角色 Redis Key
        String userRolesKey = RedisKeyConstants.buildUserRoleKey(Long.valueOf(loginId.toString()));
        // 从 Redis 获取用户的角色 Key 列表（JSON 字符串格式）
        String useRolesValue = redisTemplate.opsForValue().get(userRolesKey);

        // 如果用户没有角色数据，直接返回 null
        if (StringUtils.isBlank(useRolesValue)) {
            return null;
        }

        try {
            // 将 JSON 字符串解析为角色 Key 列表
            List<String> userRoleKeys = objectMapper.readValue(useRolesValue, new TypeReference<>() {});

            // 如果角色列表不为空
            if (CollUtil.isNotEmpty(userRoleKeys)) {
                // 批量构建角色权限 Redis Key
                List<String> rolePermissionsKeys = userRoleKeys.stream()   
                                    .map(RedisKeyConstants::buildRolePermissionsKey)
                                    .toList();

                // 使用 multiGet 批量查询，减少 Redis 往返次数，提升性能
                List<String> rolePermissionsValues = redisTemplate.opsForValue().multiGet(rolePermissionsKeys);

                // 如果查询到权限数据
                if (CollUtil.isNotEmpty(rolePermissionsValues)) {
                    List<String> permissions = Lists.newArrayList();
                    // 遍历所有角色的权限数据
                    rolePermissionsValues.forEach(jsonValue -> {
                        try {
                            // 将每个角色的权限 JSON 解析为列表并合并
                            List<String> rolePermissions = objectMapper.readValue(jsonValue, new TypeReference<>() {});
                            permissions.addAll(rolePermissions);
                        } catch (JsonProcessingException e) {
                            log.error("==> JSON 解析错误: ", e);
                        }
                    });
                    // 返回合并后的所有权限
                    return permissions;
                }
            }
            return null;
        } catch (Exception e) {
            log.error("==> 获取用户权限列表失败: ", e);
        }
        return Collections.emptyList();
    }


    /**
     * 获取用户的角色列表
     * 
     * <p>
     * 该方法被 Sa-Token 框架在调用 {@link cn.dev33.satoken.stp.StpUtil#checkRole(String)}
     * 时自动调用，返回当前用户拥有的所有角色 Key 列表。
     * </p>
     * 
     * <p>
     * <strong>数据获取流程：</strong>
     * <ol>
     *   <li>构建 Redis Key: {@code user:roles:{userId}}</li>
     *   <li>获取用户的角色 Key 列表（JSON 字符串格式）</li>
     *   <li>将 JSON 字符串解析为角色列表并返回</li>
     * </ol>
     * </p>
     * 
     * @param loginId 用户登录 ID（即用户 ID）
     * @param loginType 登录类型（默认 "login"）
     * @return 用户拥有的角色 Key 列表，如果用户无角色或查询失败返回 null
     */
    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        try {
            log.info("## 获取用户角色列表, loginId: {}", loginId);

            // 构建用户角色 Redis Key
            String userRolesKey = RedisKeyConstants.buildUserRoleKey(Long.valueOf(loginId.toString()));
            // 从 Redis 获取用户的角色 Key 列表（JSON 字符串格式）
            String useRolesValue = redisTemplate.opsForValue().get(userRolesKey);

            // 如果用户没有角色数据，直接返回 null
            if (StringUtils.isBlank(useRolesValue)) {
                return null;
            }

            // 将 JSON 字符串解析为角色 Key 列表并返回
            return objectMapper.readValue(useRolesValue, new TypeReference<>() {});
        } catch (Exception e) {
            log.error("==> 获取用户角色列表失败: ", e);
            return null;
        }
    }

}
