package com.tefire.auth.filter;

import java.io.IOException;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.tefire.framework.common.constant.GlobalConstants;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-06-23 20:47:00
 * @Description: 提取请求头中的用户 ID 保存到上下文中，以方便后续使用
 */
@Slf4j
@Component // HeaderUserId2ContextFilter 继承自 OncePerRequestFilter，确保每个请求只会执行一次过滤操
public class HeaderUserId2ContextFilter extends OncePerRequestFilter {
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String userId = request.getHeader(GlobalConstants.USER_ID);

        log.info("## HeaderUserId2ContextFilter, 用户 ID: {}", userId);
		
        // 判断请求头中是否存在 UserID
        if (StringUtils.isBlank(userId)) {
            filterChain.doFilter(request, response);
            return;
        }

        // 如果 header 中存在 userId，则设置到 ThreadLocal 中
        log.info("===== 设置 userId 到 ThreadLocal 中， 用户 ID: {}", userId);

        LoginUserContextHolder.setUserId(userId);
		// 将请求和响应传递给过滤链中的下一个过滤器。
        filterChain.doFilter(request, response);

        try {
            filterChain.doFilter(request, response);
        } catch (Exception e) {
            // 一定要删除 ThreadLocal ，防止内存泄露
            LoginUserContextHolder.remove();
            log.info("===== 删除 ThreadLocal， userId: {}", userId);
        }
    }
}
