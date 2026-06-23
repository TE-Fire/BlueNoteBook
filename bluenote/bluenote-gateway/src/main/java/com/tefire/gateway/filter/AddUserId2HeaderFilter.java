package com.tefire.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import com.tefire.framework.common.constant.GlobalConstants;

import cn.dev33.satoken.stp.StpUtil;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-06-23 16:31:41
 * @Description: 转发请求时，将用户 ID 添加到 Header 请求头中，透传给下游服务
 */
@Slf4j
@Component
public class AddUserId2HeaderFilter implements GlobalFilter {
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        log.info("==================> TokenConvertFilter");

        Long userId = null;
        try {
            // 获取当前登录用户的 ID
            userId = StpUtil.getLoginIdAsLong();
        } catch (Exception e) {
            // 若没有登录，则直接放行
            return chain.filter(exchange);
        }
        log.info("## 当前登录的用户 ID: {}", userId);

        Long finalUserId = userId;
      ServerWebExchange newExchange = exchange.mutate()
                  .request(builder -> builder.header(GlobalConstants.USER_ID, String.valueOf(finalUserId))) // 将用户id设置到请求头
                  .build();
		// 将请求传递给过滤器链中的下一个过滤器进行处理。没有对请求进行任何修改。
        return chain.filter(newExchange);
    }
}
