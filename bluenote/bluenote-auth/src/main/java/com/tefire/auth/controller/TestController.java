/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-06-17 20:23:47
 * @Description: 
 */
package com.tefire.auth.controller;

import java.time.LocalDateTime;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tefire.auth.domain.dataobject.UserDO;
import com.tefire.framework.biz.operationlog.aspect.ApiOperationLog;
import com.tefire.framework.common.response.Response;

import cn.dev33.satoken.stp.StpUtil;


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

    @PostMapping("/test3")
    @ApiOperationLog(description = "测试接口2")
    public Response<UserDO> test2(@RequestBody @Validated UserDO user) {
        return Response.success(user);
    }

    // 测试登录，浏览器访问： http://localhost:8080/user/doLogin?username=zhang&password=123456
    @RequestMapping("/user/doLogin")
    public String doLogin(String username, String password) {
        // 此处仅作模拟示例，真实项目需要从数据库中查询数据进行比对
        if("zhang".equals(username) && "123456".equals(password)) {
            StpUtil.login(10001);
            return "登录成功";
        }
        return "登录失败";
    }

    // 查询登录状态，浏览器访问： http://localhost:8080/user/isLogin
    @RequestMapping("/user/isLogin")
    public String isLogin() {
        return "当前会话是否登录：" + StpUtil.isLogin();
    }
}