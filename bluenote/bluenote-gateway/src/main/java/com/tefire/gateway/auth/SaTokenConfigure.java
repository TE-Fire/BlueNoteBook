package com.tefire.gateway.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import cn.dev33.satoken.reactor.filter.SaReactorFilter;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;

/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-06-22 22:10:23
 * @Description: 网关配置：在gateway拦截和过滤请求
 */
@Configuration
public class SaTokenConfigure {
    // 注册 Sa-Token全局过滤器
    @Bean
    public SaReactorFilter getSaReactorFilter() {
        return new SaReactorFilter()
                // 拦截地址
                .addInclude("/**")    /* 拦截全部path */
                // 鉴权方法：每次访问进入
                .setAuth(obj -> {
                    // 登录校验
                    SaRouter.match("/**") // 拦截所有路由
                            .notMatch("/auth/user/login") // 排除登录接口
                            .notMatch("/auth/verification/code/send") // 排除验证码发送接口
                            .check(r -> StpUtil.checkLogin()) // 校验是否登录
                    ;

                    // 权限认证 -- 不同模块, 校验不同权限
                    // SaRouter.match("/auth/user/logout", r -> StpUtil.checkPermission("common_user"));
                    SaRouter.match("/auth/user/logout", r -> StpUtil.checkPermission("app:note:delete"));

                    // SaRouter.match("/auth/user/logout", r -> StpUtil.checkRole("admin"));
                    // SaRouter.match("/admin/**", r -> StpUtil.checkPermission("admin"));
                    // SaRouter.match("/goods/**", r -> StpUtil.checkPermission("goods"));
                    // SaRouter.match("/orders/**", r -> StpUtil.checkPermission("orders"));

                    // 更多匹配 ...  */
                })
                // 异常处理方法：每次setAuth函数出现异常时进入
                // .setError(e -> {
                //     return SaResult.error(e.getMessage());
                // })
                ;
    }
}
