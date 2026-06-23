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

/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-06-22 22:04:25
 * @Description: 自定义权限验证接口扩展
 */
@Component
@Slf4j
public class StpInterfaceImpl implements StpInterface{

    @Resource
    private RedisTemplate<String, String> redisTemplate;

    @Resource
    private ObjectMapper objectMapper;


    /**
     * 获取用户权限列表
     *
     * @param loginId
     * @param loginType
     * @return
     */
    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        log.info("## 获取用户权限列表, loginId: {}", loginId);

        String userRolesKey = RedisKeyConstants.buildUserRoleKey(Long.valueOf(loginId.toString()));
        String useRolesValue = redisTemplate.opsForValue().get(userRolesKey);

        if (StringUtils.isBlank(useRolesValue)) {
            return null;
        }

        try {
            List<String> userRoleKeys = objectMapper.readValue(useRolesValue, new TypeReference<>() {});

            if (CollUtil.isNotEmpty(userRoleKeys)) {
                List<String> rolePermissionsKeys = userRoleKeys.stream()   
                                    .map(RedisKeyConstants::buildRolePermissionsKey)
                                    .toList();

                List<String> rolePermissionsValues = redisTemplate.opsForValue().multiGet(rolePermissionsKeys);

                if (CollUtil.isNotEmpty(rolePermissionsValues)) {
                    List<String> permissions = Lists.newArrayList();
                    rolePermissionsValues.forEach(jsonValue -> {
                        try {
                            List<String> rolePermissions = objectMapper.readValue(jsonValue, new TypeReference<>() {});
                            permissions.addAll(rolePermissions);
                        } catch (JsonProcessingException e) {
                            log.error("==> JSON 解析错误: ", e);
                        }
                    });
                    return permissions;
                }
            }
            return null;
        } catch (Exception e) {
            log.error("==> JSON 解析错误: ", e);
        }
        return Collections.emptyList();
    }


    /**
     * 获取用户角色列表
     *
     * @param loginId
     * @param loginType
     * @return
     */
    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        try {
            log.info("## 获取用户角色列表, loginId: {}", loginId);

            String userRolesKey = RedisKeyConstants.buildUserRoleKey(Long.valueOf(loginId.toString()));
            String useRolesValue = redisTemplate.opsForValue().get(userRolesKey);

            if (StringUtils.isBlank(useRolesValue)) {
                return null;
            }

            return objectMapper.readValue(useRolesValue, new TypeReference<>() {});
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

}
