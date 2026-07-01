package com.tefire.note.biz.rpc;

import java.util.Objects;

import org.springframework.stereotype.Component;

import com.tefire.framework.common.response.Response;
import com.tefire.user.api.UserFeignApi;
import com.tefire.user.dto.req.FindUserByIdReqDTO;
import com.tefire.user.dto.resp.FindUserByIdRspDTO;

import jakarta.annotation.Resource;

/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-07-01 21:24:08
 * @Description: rpc 调用用户服务
 */
@Component
public class UserRpcService {
    
    @Resource
    private UserFeignApi userFeignApi;

     /**
     * 查询用户信息
     * @param userId
     * @return
     */
    public FindUserByIdRspDTO findById(Long userId) {
        FindUserByIdReqDTO findUserByIdReqDTO = new FindUserByIdReqDTO();

        findUserByIdReqDTO.setId(userId);

        Response<FindUserByIdRspDTO> response = userFeignApi.findById(findUserByIdReqDTO);

        if (Objects.isNull(response) || !response.isSuccess()) {
            return null;
        }

        return response.getData();
    }
}
