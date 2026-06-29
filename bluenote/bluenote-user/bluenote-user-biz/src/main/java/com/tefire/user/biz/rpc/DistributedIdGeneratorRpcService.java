package com.tefire.user.biz.rpc;

import org.springframework.stereotype.Component;

import com.tefire.generator.api.DistributedIdGeneratorFeignApi;

import jakarta.annotation.Resource;

@Component
public class DistributedIdGeneratorRpcService {
    
    @Resource
    private DistributedIdGeneratorFeignApi distributedIdGeneratorFeignApi;

     /**
     * Leaf 号段模式：小蓝书 ID 业务标识
     */
    private static final String BIZ_TAG_BlUENOTE_ID = "leaf-segment-bluenote-id";

    /**
     * Leaf 号段模式：用户 ID 业务标识
     */
    private static final String BIZ_TAG_USER_ID = "leaf-segment-user-id";

    /**
     * 调用分布式 ID 生成服务生成小蓝书 ID
     *
     * @return
     */
    public String getBluenoteId() {
        return distributedIdGeneratorFeignApi.getSegmentId(BIZ_TAG_BlUENOTE_ID);
    }

    /**
     * 调用分布式 ID 生成服务用户 ID
     *
     * @return
     */
    public String getUserId() {
        return distributedIdGeneratorFeignApi.getSegmentId(BIZ_TAG_USER_ID);
    }
}
