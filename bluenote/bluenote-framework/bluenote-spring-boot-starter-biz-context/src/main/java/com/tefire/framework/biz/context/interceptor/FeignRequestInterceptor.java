package com.tefire.framework.biz.context.interceptor;

import java.util.Objects;

import com.tefire.framework.biz.context.holder.LoginUserContextHolder;
import com.tefire.framework.common.constant.GlobalConstants;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.extern.slf4j.Slf4j;

/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-06-26 21:28:03
 * @Description: 配置Feign 拦截器，设置网关渗透过来的 ID
 */
@Slf4j
public class FeignRequestInterceptor  implements RequestInterceptor{
    
    @Override
    public void apply(RequestTemplate requestTemplate) {
        // 获取当前上下文中的用户 ID
        Long userId = LoginUserContextHolder.getUserId();

        // 若不为空，则添加到请求头中
        if (Objects.nonNull(userId)) {
            requestTemplate.header(GlobalConstants.USER_ID, String.valueOf(userId));
            log.info("########## feign 请求设置请求头 userId: {}", userId);
        }
    }
}
