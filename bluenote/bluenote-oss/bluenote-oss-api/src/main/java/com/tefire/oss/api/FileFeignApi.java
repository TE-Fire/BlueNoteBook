/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-06-26 12:31:24
 * @Description: 
 */
package com.tefire.oss.api;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;

import com.tefire.framework.common.response.Response;
import com.tefire.oss.constant.ApiConstants;

@FeignClient(name = ApiConstants.SERVICE_NAME)
public interface FileFeignApi {

    String PREFIX = "/file";

    @PostMapping(value = PREFIX + "/test")
    Response<?> test();
}
