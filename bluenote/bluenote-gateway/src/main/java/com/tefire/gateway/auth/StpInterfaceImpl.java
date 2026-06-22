package com.tefire.gateway.auth;

import java.util.Collections;
import java.util.List;

import cn.dev33.satoken.stp.StpInterface;

/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-06-22 22:04:25
 * @Description: 自定义权限验证接口扩展
 */
public class StpInterfaceImpl implements StpInterface{
    @Override
    public List<String> getPermissionList(Object arg0, String arg1) {
        // 返回此 loginId 拥有的权限列表
        
        // todo 从 redis 获取
        
        return Collections.emptyList();
    }

    @Override
    public List<String> getRoleList(Object arg0, String arg1) {
        // 返回此 loginId 拥有的角色列表
        
        // todo 从 redis 获取
        
        return Collections.emptyList();
    }


}
