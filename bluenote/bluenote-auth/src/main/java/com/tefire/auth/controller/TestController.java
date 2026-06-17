package com.tefire.auth.controller;

import java.time.LocalDateTime;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tefire.framework.biz.operationlog.aspect.ApiOperationLog;
import com.tefire.framework.common.response.Response;

/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-06-17 20:27:53
 * @Description: 
 */
@RestController
public class TestController {
    @GetMapping("/test")
    @ApiOperationLog(description = "测试接口")
    public Response<String> test() {
        return Response.success("Hello, 犬小哈专栏");
    }

    @GetMapping("/test2")
    @ApiOperationLog(description = "测试接口2")
    public Response<User> test2() {
        return Response.success(User.builder()
                        .nickName("犬小哈")
                        .createTime(LocalDateTime.now())
                        .build());
    }
}
