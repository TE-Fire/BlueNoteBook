package com.tefire.generator.api;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.tefire.generator.constant.ApiConstants;

/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-06-29 22:33:08
 * @Description: feign 调用 生成唯一 id 服务
 */
@FeignClient(name = ApiConstants.SERVICE_NAME)
public interface DistributedIdGeneratorFeignApi {

    String PREFIX = "/id";

    @GetMapping(value = PREFIX + "/segment/get/{key}")
    String getSegmentId(@PathVariable("key") String key);

    @GetMapping(value = PREFIX + "/snowflake/get/{key}")
    String getSnowflakeId(@PathVariable("key") String key);

}