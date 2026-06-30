package com.tefire.note.biz.rpc;

import org.springframework.stereotype.Component;

import com.tefire.generator.api.DistributedIdGeneratorFeignApi;

import jakarta.annotation.Resource;

/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-06-30 16:34:23
 * @Description: 调用生成 id 服务为笔记生成 id
 */
@Component
public class DistributedIdGeneratorRpcService {
    
    @Resource
    private DistributedIdGeneratorFeignApi distributedIdGeneratorFeignApi;

     /**
     * 生成雪花算法 ID
     *
     * @return
     */
    public String getSnowflakeId() {
        return distributedIdGeneratorFeignApi.getSnowflakeId("test");
    }
}
