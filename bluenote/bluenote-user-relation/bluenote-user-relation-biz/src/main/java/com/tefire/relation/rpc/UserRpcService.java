package com.tefire.relation.rpc;

import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.tefire.framework.common.response.Response;
import com.tefire.user.api.UserFeignApi;
import com.tefire.user.dto.req.FindUserByIdReqDTO;
import com.tefire.user.dto.req.FindUsersByIdsReqDTO;
import com.tefire.user.dto.resp.FindUserByIdRspDTO;

import cn.hutool.core.collection.CollUtil;
import jakarta.annotation.Resource;

/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-07-07 14:58:58
 * @Description: RPC 调用
 */
@Component
public class UserRpcService {
    
    @Resource
    private UserFeignApi userFeignApi;

       /**
     * 根据用户 ID 查询
     *
     * @param userId
     * @return
     */
    public FindUserByIdRspDTO findById(Long userId) {
        FindUserByIdReqDTO findUserByIdReqDTO = new FindUserByIdReqDTO();
        findUserByIdReqDTO.setId(userId);

        Response<FindUserByIdRspDTO> response = userFeignApi.findById(findUserByIdReqDTO);

        if (!response.isSuccess() || Objects.isNull(response)) {
            return null;
        }

        return response.getData();
    }

    /**
     * 批量查询用户信息
     * 
     * @param userIds
     * @return
     */
    public List<FindUserByIdRspDTO> findByIds(List<Long> userIds) {
        FindUsersByIdsReqDTO findUsersByIdsReqDTO = new FindUsersByIdsReqDTO();
        findUsersByIdsReqDTO.setIds(userIds);

        Response<List<FindUserByIdRspDTO>> response = userFeignApi.findByIds(findUsersByIdsReqDTO);

        if (!response.isSuccess() || Objects.isNull(response.getData()) || CollUtil.isEmpty(response.getData())) {
            return null;
        }

        return response.getData();
    }
}
