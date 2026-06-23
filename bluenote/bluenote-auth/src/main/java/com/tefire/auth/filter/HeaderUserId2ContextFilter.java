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

/**
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-06-23 20:47:00
 * @Description: 请求头用户ID上下文过滤器
 * 
 */
@Slf4j
@Component
public class HeaderUserId2ContextFilter extends OncePerRequestFilter {
    
    /**
     * 执行过滤逻辑
     * 
     * @param request  HTTP 请求对象
     * @param response HTTP 响应对象
     * @param filterChain 过滤器链，用于调用后续过滤器或目标资源
     * @throws ServletException  Servlet 异常
     * @throws IOException      IO 异常
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        // 从请求头中提取用户ID
        String userId = request.getHeader(GlobalConstants.USER_ID);
        log.info("## HeaderUserId2ContextFilter, 用户 ID: {}", userId);
		
        // 如果请求头中没有用户ID，直接放行，不设置上下文
        if (StringUtils.isBlank(userId)) {
            filterChain.doFilter(request, response);
            return;
        }

        // 将用户ID设置到 ThreadLocal 上下文，供后续业务使用
        log.info("===== 设置 userId 到 ThreadLocal 中，用户 ID: {}", userId);
        LoginUserContextHolder.setUserId(userId);
		
        try {
            // 调用过滤器链中的下一个过滤器或目标资源
            filterChain.doFilter(request, response);
        } finally {
            // 必须在 finally 块中清理 ThreadLocal，防止内存泄漏和数据污染
            // 无论请求成功还是失败（包括抛出异常），都会执行此清理逻辑
            LoginUserContextHolder.remove();
            log.info("===== 删除 ThreadLocal，用户 ID: {}", userId);
        }
    }
}
