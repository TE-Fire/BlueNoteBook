package com.tefire.auth.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tefire.framework.common.response.Response;

/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-06-17 20:27:53
 * @Description: 
 */
@RestController
public class TestController {
    @GetMapping("/test")
    public Response<String> test() {
        return Response.success("Hello, 犬小哈专栏");
    }
}
